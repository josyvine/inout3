package com.inout.app;

import com.inout.app.models.AttendanceRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Utility class to generate a full monthly report.
 * It merges real Firestore data with generated "Absent" dates.
 * FIXED: Ensures the Day of Week is calculated for every record.
 */
public class AttendanceReportManager {

    /**
     * Generates a list containing every day of the current month.
     * 
     * @param logs A Map where the Key is the Date String (yyyy-MM-dd) 
     *             and the Value is the real AttendanceRecord from Firestore.
     * @return A full list of AttendanceRecords for the entire month.
     */
    public static List<AttendanceRecord> generateFullMonthList(Map<String, AttendanceRecord> logs) {
        List<AttendanceRecord> fullList = new ArrayList<>();
        
        // 1. Get the current calendar instance
        Calendar calendar = Calendar.getInstance();
        
        // 2. Set to the first day of the current month
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        
        // 3. Determine how many days are in this month (28, 29, 30, or 31)
        int totalDaysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);

        // 4. Setup date formatters
        SimpleDateFormat dateIdFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        // This formatter specifically extracts the Day name (e.g., Monday)
        SimpleDateFormat dayNameFormat = new SimpleDateFormat("EEEE", Locale.US);

        // 5. Loop through every day of the month from 1 to totalDaysInMonth
        for (int i = 1; i <= totalDaysInMonth; i++) {
            String dateId = dateIdFormat.format(calendar.getTime());
            String dayName = dayNameFormat.format(calendar.getTime());

            if (logs.containsKey(dateId)) {
                // DATA EXISTS: Get the real record from Firestore
                AttendanceRecord realRecord = logs.get(dateId);
                
                if (realRecord != null) {
                    // FIXED: Set the day name calculated from the calendar
                    realRecord.setDayOfWeek(dayName);
                    fullList.add(realRecord);
                }
            } else {
                // DATA MISSING: Create a professional "Absent" record for this date
                AttendanceRecord absentRecord = new AttendanceRecord();
                absentRecord.setDate(dateId);
                
                // FIXED: Set the day name calculated from the calendar
                absentRecord.setDayOfWeek(dayName);
                
                // Note: Fields like totalHours, checkInTime, etc., stay null.
                // The AttendanceAdapter and getStatus() logic will show this as "Absent".
                
                fullList.add(absentRecord);
            }

            // Move the calendar to the next day for the next iteration
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        return fullList;
    }

    /**
     * Helper to get the display string for the report header (e.g., "January 2026")
     */
    public static String getCurrentMonthYearString() {
        Calendar cal = Calendar.getInstance();
        return new SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.getTime());
    }
}