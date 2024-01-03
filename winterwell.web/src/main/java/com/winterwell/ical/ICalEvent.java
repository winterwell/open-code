package com.winterwell.ical;


import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;

import com.winterwell.utils.StrUtils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.time.Dt;
import com.winterwell.utils.time.Period;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.web.WebUtils2;

public class ICalEvent {

	TimeZone timezone;
	public Time start;
	/**
	 * Can be null -- interpreted as one day.
	 * See https://stackoverflow.com/questions/15295887/does-an-ical-file-have-to-have-an-enddate
	 */
	public Time end;
	public String summary;
	public String description;
	/**
	 * This can be shared by repeat events!
	 * See https://www.kanzaki.com/docs/ical/uid.html
	 */
	public String uid;
	public Time created;
	public String location;
	public String raw;
	Repeat repeat;
	
	/**
	 * (Non standard) video conf call url e.g. from X-GOOGLE-CONFERENCE
	 */
	public String conference;
	
	/**
	 * e.g. "RECURRENCE-ID;TZID=Europe/London:20210712T120000"
	 * 
	 * See https://www.kanzaki.com/docs/ical/recurrenceId.html
	 */
	public String recurrenceId;
	/**
	 * Used by locally-generated repeating events (can be null) 
	 */
	public ICalEvent parent;
	public List<Attendee> attendees = new ArrayList(); // TODO

	
	public ICalEvent() {
	}
	
	public void setSrc(String src) {
		this.raw = src;
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(recurrenceId, start, summary, uid);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ICalEvent other = (ICalEvent) obj;
		boolean eq = Objects.equals(recurrenceId, other.recurrenceId) && Objects.equals(start, other.start)
				&& Objects.equals(summary, other.summary) && Objects.equals(uid, other.uid);
		return eq;
	}

	/**
	 * 
	 * @param start
	 * @param end Can be null
	 * @param summary
	 */
	public ICalEvent(Time start, Time end, String summary) {
		this.start = start;
		this.end = end;
		this.summary = summary;
		if (start==null) throw new NullPointerException("null start not allowed for ICalEvent");
	}

	/**
	 * @return the ical string
	 */
	public String toICal() {
		if (raw!=null) return raw;
		String s = ICalWriter.format(start);
		return "BEGIN:VEVENT\r\n"
			+"DTSTART:"+s+"\r\n" // FIXME What is the format???
			+(end!=null? "DTEND:"+ICalWriter.format(end)+"\r\n" : "")				
			// TODO UID and others!
			+(summary==null? "" : "SUMMARY:"+ICalWriter.formatText(summary)+"\r\n")
			+(uid==null? "" : "UID:"+ICalWriter.formatText(uid)+"\r\n")
			+(description==null? "" : "DESCRIPTION:"+ICalWriter.formatText(description)+"\r\n")
			+(conference==null? "" : "X-CONFERENCE:"+ICalWriter.formatText(conference)+"\r\n") // non-standard, Google uses
			+(location==null? "" : "LOCATION:"+ICalWriter.formatText(location)+"\r\n")
			+(repeat==null? "" : "RRULE:"+repeat.getRrule()+"\r\n")
			+"END:VEVENT\r\n";
	}
	
	@Override
	public String toString() {
		return "ICalEvent["+StrUtils.joinWithSkip(" ", start, summary, uid, conference)+"]"; // trimmed down for readable logs
	}
	
	public String getGoogleCalendarLink() {
		return WebUtils2.addQueryParameters("https://www.google.com/calendar/render?action=TEMPLATE",
				new ArrayMap("text",summary,
						"dates", ICalWriter.format(start)+"/"+ICalWriter.format(end),
						"details",description,
						"location",location
//						"sf",true,
//						"output","xml"
						));
	}
	
	public boolean isRepeating() {
		return repeat!=null;
	}

	/**
	 * @param start
	 * @param end
	 * @return All repeats within start and end, if it is repeating.
	 * If not -- return null.
	 */
	public List<ICalEvent> getRepeats(Time rstart, Time rend) {
		if (repeat==null) return null;
		assert Objects.equals(repeat.timezone, timezone);
		List<Time> repeatPeriods = repeat.getRepeats(rstart, rend);
		List<ICalEvent> repeatEvents = new ArrayList();
		Dt dt = end==null? null : start.dt(end);
		for (Time t : repeatPeriods) {			
			Time repEnd = dt==null? null : t.plus(dt);
			ICalEvent e2 = new ICalEvent(t, repEnd, summary);
			e2.description = description;
			e2.location = location;
			e2.parent = this;
			e2.recurrenceId = recurrenceId;
			e2.uid = uid; // What? yes, uid is not unique across repeats
			repeatEvents.add(e2);
		}
		return repeatEvents;
	}

	public void setRepeat(Repeat repeater) {
		// TODO modify raw
		this.repeat = repeater;
		if (repeat.since==null) repeat.setSince(start);
		assert repeat.since.equals(start) : repeat+" vs "+this;
	}

	public Period getPeriod() {
		return new Period(start, end);
	}
	
	public Repeat getRepeat() {
		return repeat;
	}

	/**
	 * 
	 * @param email
	 * @return true/null
	 */
	public Boolean isAttending(String email) {
		List<Attendee> _attendees = getAttendees();
		for (Attendee attendee : _attendees) {
			if (email.equalsIgnoreCase(attendee.getEmail())) {
				return attendee.isAttending();
			}
		}
		return null;
	}

	private List<Attendee> getAttendees() {
		return attendees;
	}
}

