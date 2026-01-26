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

        // 4. Setup date formatters to match your CSV requirements
        SimpleDateFormat dateIdFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat dayNameFormat = new SimpleDateFormat("EEEE", Locale.US);

        // 5. Loop through every day of the month
        for (int i = 1; i <= totalDaysInMonth; i++) {
            String dateId = dateIdFormat.format(calendar.getTime());
            String dayName = dayNameFormat.format(calendar.getTime());

            if (logs.containsKey(dateId)) {
                // DATA EXISTS: Add the real record from Firestore
                AttendanceRecord realRecord = logs.get(dateId);
                // Ensure the Day of Week is set for the table
                if (realRecord != null) {
                    realRecord.setDayOfWeek(dayName);
                    fullList.add(realRecord);
                }
            } else {
                // DATA MISSING: Create a professional "Absent" record for this date
                AttendanceRecord absentRecord = new AttendanceRecord();
                absentRecord.setDate(dateId);
                absentRecord.setDayOfWeek(dayName);
                
                // Fields like checkInTime and checkOutTime remain NULL.
                // This triggers the "ic_status_absent" icon in the Adapter.
                
                fullList.add(absentRecord);
            }

            // Move to the next day
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