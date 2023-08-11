package com.goodloop.gcal;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

import org.junit.Test;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Calendar;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventReminder;
import com.winterwell.ical.ICalEvent;
import com.winterwell.utils.Printer;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.time.Dt;
import com.winterwell.utils.time.Period;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.time.TimeOfDay;
import com.winterwell.utils.time.TimeUtils;

public class GCalClientTest {

	@Test
	public void testgetEventsDan() {
		Period today = new Period(new Time().minus(8, TUnit.HOUR), new Time().plus(8, TUnit.HOUR));
		GCalClient client = new GCalClient();
		String userEmail = "daniel@good-loop.com";
		Calendar c = client.getCalendar(userEmail);
		String cid = c.getId();
		List<Event> allEvents = client.getEvents(cid, today.first, today.second);
		List<ICalEvent> icals = Containers.apply(allEvents, GCalClient::toICalEvent);
	}

	@Test
	public void testgetEventsDanMissingEventBug() {
		Period today = new Period(new Time(2022,9,6,14,0,0), new Time(2022,9,6,19,0,0));
		GCalClient client = new GCalClient();
		String userEmail = "daniel@good-loop.com";
		Calendar c = client.getCalendar(userEmail);
		String cid = c.getId();
		List<Event> allEvents = client.getEvents(cid, today.first, today.second);
		List<ICalEvent> icals = Containers.apply(allEvents, GCalClient::toICalEvent);
		System.out.println(icals);
		assert icals.toString().contains("ytc-veii-eyn") : icals;
	}

	@Test
	public void testGCalSmokeTest() throws IOException {
		GCalClient gcc = new GCalClient();
		List<CalendarListEntry> list = gcc.getCalendarList();
		// hm - doesn't have all of GL there
		for (CalendarListEntry calendarListEntry : list) {
			Printer.out(calendarListEntry.getId() // just their email!
					+"	"+calendarListEntry.getSummary()
					+"	"+calendarListEntry.getDescription()
//					+"	"+calendarListEntry.getKind() boring
					+"	"+calendarListEntry.getAccessRole()
					);
		}
	}

	@Test
	public void testMake1to1() throws IOException {
		GCalClient gcc = new GCalClient();
		Calendar dw = gcc.getCalendar("wing@good-loop.com");
		
		System.out.println(dw);
		Calendar dw2 = gcc.getCalendar("daniel@good-loop.com");
		System.out.println(dw2);		
//		Calendar da = gcc.getCalendar("daniel.appel.winterwell@gmail.com");
//		System.out.println(da);
		
		Event event = new Event()
			    .setSummary("A Test by Dan W - please ignore this! #ChatRoundabout "+Utils.getNonce())
			    .setDescription("A lovely event")
			    ;

			DateTime startDateTime = new DateTime(new Time().toISOString());
			EventDateTime start = new EventDateTime()
			    .setDateTime(startDateTime)
			    .setTimeZone("GMT");
			event.setStart(start);

			DateTime endDateTime = new DateTime(new Time().plus(TUnit.HOUR).toISOString());
			EventDateTime end = new EventDateTime()
			    .setDateTime(endDateTime)
			    .setTimeZone("GMT");
			event.setEnd(end);

//			String[] recurrence = new String[] {"RRULE:FREQ=DAILY;COUNT=2"};
//			event.setRecurrence(Arrays.asList(recurrence));

			EventAttendee[] attendees = new EventAttendee[] {
			    new EventAttendee().setEmail("daniel@good-loop.com")
			    	.setResponseStatus("tentative"),
			    new EventAttendee().setEmail("daniel.winterstein@gmail.com")
			    	.setResponseStatus("tentative"),
			};
			event.setAttendees(Arrays.asList(attendees));

			EventReminder[] reminderOverrides = new EventReminder[] {
			    new EventReminder().setMethod("email").setMinutes(10),
			    new EventReminder().setMethod("popup").setMinutes(1),
			};
			Event.Reminders reminders = new Event.Reminders()
			    .setUseDefault(false)
			    .setOverrides(Arrays.asList(reminderOverrides));
			event.setReminders(reminders);

			String calendarId = dw.getId(); // "primary";
			Event event2 = gcc.addEvent(calendarId, event, false, true);
			
			Printer.out(event2.toPrettyString());
		}
	
	@Test
	public void getEvents() {
		GCalClient gcc = new GCalClient();
		Calendar ww = gcc.getCalendar("wing@good-loop.com");
		System.out.println(ww.getId());
		
		LocalDate _nextFriday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
		Time nextFriday = new Time(_nextFriday.toString());
		Time s = new TimeOfDay("11:00am").set(nextFriday);
		Time e = s.plus(new Dt(10, TUnit.MINUTE));
		Period slot = new Period(s, e);
		
		Time start = TimeUtils.getStartOfDay(slot.first.minus(TUnit.DAY));
		Time end = TimeUtils.getStartOfDay(slot.first.plus(TUnit.DAY));
		
		List<Event> events = gcc.getEvents(ww.getId(), start, end);
		for (Event event : events) {
			System.out.println(event + "\n");
		}
	}
	
	@Test
	public void testTimeZonePeroid() {
		LocalDate _nextFriday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
		Time nextFriday = new Time(_nextFriday.toString());
		Time s = new TimeOfDay("11:00am").set(nextFriday);
		Time e = s.plus(new Dt(10, TUnit.MINUTE));
		
		TimeZone tz = TimeZone.getDefault();
		System.out.println(tz.getDSTSavings());
		
		Period slot = new Period(s.minus(tz.getDSTSavings(), TUnit.MILLISECOND), e.minus(tz.getDSTSavings(), TUnit.MILLISECOND));
		System.out.println(slot);
	}
}
