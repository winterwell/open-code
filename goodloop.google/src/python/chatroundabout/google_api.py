from datetime import datetime, timezone, timedelta, date
from google.oauth2 import service_account
from googleapiclient.discovery import build
import random
import string

from email_client_smtp import Email_Client_SMTP
from employee import Employee
EVENT_NAME = '#ChatRoundabout'


class GoogleAPI:
    def __init__(self, scopes: str, service_account_path: str):
        self.scopes: list[str] = scopes
        self.service_account_path = service_account_path
        self.service = self.refresh_service()

    def refresh_service(self, account='alerts@good-loop.com'):
        credentials = service_account.Credentials.from_service_account_file(
            self.service_account_path, scopes=self.scopes).with_subject(account)
        service = build('calendar', 'v3', credentials=credentials)
        return service

    def get_calendar_id(self, email: str) -> (str | None):
        calendar_list = self.service.calendarList().list().execute()
        calendars = calendar_list.get('items', [])
        for calendar in calendars:
            if calendar['id'] == email:
                return str(calendar['id'])
        return None

    def get_events(self, calendar_id: str, date: datetime) -> list[dict]:
        date = date.strftime('%Y-%m-%d')

        events_result = self.service.events().list(
            calendarId=calendar_id,
            timeMin=date + 'T00:00:00Z',
            timeMax=date + 'T23:59:59Z',
            singleEvents=True,
            orderBy='startTime'
        ).execute()

        events = events_result.get('items', [])
        return events

    def get_partner(self, calendar_id: str, date: datetime) -> str | None:
        events = self.get_events(calendar_id, date)

        for event in events:
            if 'summary' not in event:
                continue
            if EVENT_NAME in event['summary']:
                for attendee in event['attendees']:
                    if (attendee['email']) != calendar_id:
                        return attendee['email']

        return None

    def check_availability(self, calendar_id: str, date: datetime, timeslot: dict[str, datetime]) -> tuple[bool, str]:
        '''Return true if the person is available, and the reason of unavailability 
        '''
        def check_intersection(start1, end1, start2, end2):
            return start1 < end2 and end1 > start2

        events = self.get_events(calendar_id, date)

        for event in events:
            if 'summary' not in event:
                event['summary'] = 'Private Event'

            # Check holidays
            if 'holiday' in str(event['summary']).lower() or 'approved leave' in str(event['summary']).lower():
                return False, str(event['summary'])

            # Skip all day events except for holiday
            if 'dateTime' not in event['start']:
                continue

            # Check intersection
            event_start = datetime.fromisoformat(
                str(event['start']['dateTime']))
            event_end = datetime.fromisoformat(str(event['end']['dateTime']))
            if check_intersection(timeslot['start'], timeslot['end'], event_start, event_end):
                return False, str(event['summary'])

        return True, ''

    def send_event_email(self, attendee: Employee, partner: Employee, timeslot_start: datetime, event_link: str, email_client: Email_Client_SMTP):
        subject: str = 'Chatroundabout: Notification'
        body: str = f''' Hello {attendee.first_name}, 
        <br/><br/>
        This is an automated message to let you know that you have a Chat Roundabout meeting with {partner.first_name} on {timeslot_start.day}/{timeslot_start.month}/{timeslot_start.year}. Please accept the event on your calender here: {event_link}
        <br/><br/>
        If you're unable to make it, please contact your Chat Roundabout partner to let them know and arrange a new time slot for your 121.
        <br/><br/>
        Enjoy the rest of your day!
        <br/><br/>
        I am a bot, beep boop.
        '''

        email_client.send_email_message(body, attendee.email, subject)

    def create_121_event(self, attendees: list[Employee], timeslot: dict[str, datetime], email_client: Email_Client_SMTP | None):
        eventBody = {
            'summary': f'#ChatRoundabout all-team 121 {attendees[0].first_name} <> {attendees[1].first_name}',
            'description': f'Random short weekly chat between {attendees[0].first_name} and {attendees[1].first_name}. Talk about anything you like.',
            'start': {
                'dateTime': timeslot['start'].isoformat(),
                'timeZone': 'UTC',
            },
            'end': {
                'dateTime': timeslot['end'].isoformat(),
                'timeZone': 'UTC',
            },
            'attendees': [
                {'email': attendees[0].email},
                {'email': attendees[1].email},
            ],
            'conferenceData': {
                'createRequest': {
                    'requestId': ''.join(random.choices(string.ascii_letters, k=12)),
                    'conferenceSolutionKey': {
                        'type': 'hangoutsMeet'
                    }
                }
            },
            'reminders': {
                'useDefault': False,
                'overrides': [
                    {'method': 'popup', 'minutes': 10},
                ],
            },
        }

        self.refresh_service(attendees[0].email)
        event = self.service.events().insert(
            calendarId='primary', conferenceDataVersion=1, body=eventBody).execute()
        print(
            f'Event created for {attendees[0].email} & {attendees[1].email}: {event.get("htmlLink")}')

        # Sening Notifcation Emails
        if email_client != None:
            self.send_event_email(attendees[0], attendees[1], timeslot['start'], event.get(
                "htmlLink"), email_client)
            self.send_event_email(attendees[1], attendees[0], timeslot['start'], event.get(
                "htmlLink"), email_client)

        return event


# if __name__ == '__main__':
#     api = GoogleAPI(["https://www.googleapis.com/auth/calendar"],
#                     '/home/wing/winterwell/logins/google.good-loop.com/credentials_serviceaccount.json')

#     today = date.today()
#     duration_timedelta = timedelta(minutes=10)
#     start_time = datetime(today.year, today.month, today.day,
#                           13, 20, tzinfo=timezone.utc)
#     end_time = start_time + duration_timedelta
#     timeslot = {
#         "start": start_time,
#         "end": end_time
#     }

#     api.create_121_event([Employee('Wing', 'Wong', 'wing@good-loop.com', 'Edinburgh', 'Data'),
#                          Employee('Wing2', 'Wong', 'me@good-loop.com', 'Edinburgh', 'Data')], timeslot, None)
#     pass
