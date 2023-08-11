from datetime import datetime, timedelta, date

import random
from employee import Employee, read_employees_from_csv
from google_api import *
from email_client_smtp import Email_Client_SMTP
import configparser
import json

config = configparser.ConfigParser()
config.read('./config.ini')

EMPOLYEE_FILE_PATH = config.get('Paths', 'empolyee_list')
EMAIL_CONFIG_PATH = config.get('Paths', 'email_config_path')
SERVICE_ACCOUNT_PATH = config.get('Paths', 'service_account_path')
API_SCOPES: list[str] = eval(config.get('Configs', 'api_scopes'))
TIMESLOT: dict[str, int] = json.loads(config.get('Configs', 'timeslot'))

unavailables: list[tuple] = []


def get_next_friday():
    today = date.today()
    days_ahead = (4 - today.weekday() + 7) % 7
    if days_ahead == 0: days_ahead = 7 # Catch running on Friday
    next_friday = today + timedelta(days=days_ahead)
    return next_friday


def get_event_timeslot(date: datetime, start_time_hour: int, start_time_minute: int, duration_minute: int):
    duration_timedelta = timedelta(minutes=duration_minute)

    start_time = datetime(date.year, date.month, date.day,
                          start_time_hour, start_time_minute, tzinfo=timezone.utc)

    end_time = start_time + duration_timedelta

    return {
        "start": start_time,
        "end": end_time
    }


def get_last_friday_datetime(current_datetime: datetime) -> datetime:
    if current_datetime.weekday() == 4:  # If input datetime is Friday
        current_datetime -= timedelta(days=7)
    while current_datetime.weekday() != 4:  # Find the most recent Friday
        current_datetime -= timedelta(days=1)
    return current_datetime


def pair_employees(employees: list[Employee], seed_dateime: datetime) -> list[list[Employee]]:
    random.seed(int(seed_dateime.strftime('%Y%m%d')))
    random.shuffle(employees)

    last_friday = get_last_friday_datetime(seed_dateime)

    paired_employees = []
    remaining_employees = employees[:]

    api = GoogleAPI(['https://www.googleapis.com/auth/calendar'], SERVICE_ACCOUNT_PATH)

    while len(remaining_employees) >= 2:
        employee0_temp = remaining_employees[0]
        last_week_partner_email = api.get_partner(
            employee0_temp.email, last_friday)

        for i in range(1, len(remaining_employees)):
            employee_pair_temp = remaining_employees[i]
            if last_week_partner_email is None or last_week_partner_email != employee_pair_temp.email:
                employee0 = remaining_employees.pop(0)
                employee_pair = remaining_employees.pop(i - 1)
                paired_employees.append([employee0, employee_pair])
                break

    if len(remaining_employees) == 1:
        unavailables.append(
            (f'{remaining_employees[0].first_name} {remaining_employees[0].last_name}', 'Odd one this week, no pairs.'))

    return paired_employees


def send_weekly_report_email(email_client: Email_Client_SMTP, unavailables: list[tuple], email_to: str = 'wing@good-loop.com'):
    subject: str = f'ChatRoundabout: Weekly Report {next_friday.isoformat()}'
    body: str = f'''ChatRoundabout ran for: {next_friday.isoformat()} <br/><br/>
    No 121s for all-team: <br/><br/>
    {'<br/>'.join([', '.join(map(str, tpl)) for tpl in unavailables])}
    '''

    email_client.send_email_message(body, email_to, subject)


if __name__ == '__main__':
    next_friday = get_next_friday()
    employees: list[Employee] = read_employees_from_csv(EMPOLYEE_FILE_PATH)
    timeslot = get_event_timeslot(
        next_friday, TIMESLOT['START_TIME_HOUR'], TIMESLOT['START_TIME_MINUTES'], TIMESLOT['DURATION_MINUTES'])

    # Filter unavaliable employees
    api = GoogleAPI(API_SCOPES, SERVICE_ACCOUNT_PATH)

    # Safeky check and remove unavailables
    i = 0
    while i < len(employees):
        print('Checking', employees[i])
        available = api.check_availability(employees[i].email, next_friday, timeslot)
        if not available[0]:
            unavailables.append(
                (f'{employees[i].first_name} {employees[i].last_name}', available[1]))
            del employees[i]
        else:
            i += 1
            
    print(unavailables)

    employee_pairs = pair_employees(employees, next_friday)

    email_client = Email_Client_SMTP(EMAIL_CONFIG_PATH)
    # Create events
    for attendees in employee_pairs:
        api.create_121_event(attendees, timeslot, email_client)

    # Sending admin emails
    send_weekly_report_email(email_client, unavailables, 'wing@good-loop.com')
    send_weekly_report_email(email_client, unavailables, 'daniel@good-loop.com')
