package com.inout.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.inout.app.R;
import com.inout.app.models.AttendanceRecord;

import java.util.List;

/**
 * Professional Adapter for the 10-column CSV attendance table.
 * FIXED: Handles list recycling glitches and implements professional Absent styling.
 */
public class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder> {

    private final List<AttendanceRecord> attendanceList;

    public AttendanceAdapter(List<AttendanceRecord> attendanceList) {
        this.attendanceList = attendanceList;
    }

    @NonNull
    @Override
    public AttendanceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_attendance_row, parent, false);
        return new AttendanceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AttendanceViewHolder holder, int position) {
        AttendanceRecord record = attendanceList.get(position);

        // Reset Alpha for recycled views (prevents everything becoming gray during scroll)
        holder.tvDate.setAlpha(1.0f);
        holder.tvDay.setAlpha(1.0f);

        // 1. Date & Day (Always displayed)
        holder.tvDate.setText(record.getDate());
        holder.tvDay.setText(record.getDayOfWeek() != null ? record.getDayOfWeek() : "--");

        // 2. Check-In & Check-Out
        holder.tvIn.setText(record.getCheckInTime() != null ? record.getCheckInTime() : "--:--");
        holder.tvOut.setText(record.getCheckOutTime() != null ? record.getCheckOutTime() : "--:--");

        // 3. Total Hours
        holder.tvTotalHours.setText(record.getTotalHours() != null ? record.getTotalHours() : "0h 00m");

        // 4. Location Name
        holder.tvLocation.setText(record.getLocationName() != null ? record.getLocationName() : "N/A");

        // 5. Distance (Check-In GPS Proof)
        if (record.getCheckInTime() != null) {
            holder.tvDistance.setText(Math.round(record.getDistanceMeters()) + "m");
        } else {
            holder.tvDistance.setText("--");
        }

        // 6. Fingerprint Verification Proof (Icon)
        if (record.getCheckInTime() != null) {
            holder.ivFingerprint.setImageResource(record.isFingerprintVerified() ? 
                    R.drawable.ic_status_present : R.drawable.ic_status_absent);
        } else {
            // No Check-In means no fingerprint possible
            holder.ivFingerprint.setImageResource(R.drawable.ic_status_absent);
        }

        // 7. GPS Verification Proof (Icon)
        if (record.getCheckInTime() != null) {
            holder.ivGps.setImageResource(record.isGpsVerified() ? 
                    R.drawable.ic_status_present : R.drawable.ic_status_absent);
        } else {
            // No Check-In means no GPS proof possible
            holder.ivGps.setImageResource(R.drawable.ic_status_absent);
        }

        // 8. Overall Daily Status Logic
        String status = record.getStatus();
        if (status.equals("Present")) {
            holder.ivStatus.setImageResource(R.drawable.ic_status_present);
        } else if (status.equals("Partial")) {
            holder.ivStatus.setImageResource(R.drawable.ic_status_partial);
        } else {
            // Status: Absent
            holder.ivStatus.setImageResource(R.drawable.ic_status_absent);
            
            // Visual Polish: Gray out the row if the employee was absent
            holder.tvDate.setAlpha(0.5f);
            holder.tvDay.setAlpha(0.5f);
        }
    }

    @Override
    public int getItemCount() {
        return attendanceList.size();
    }

    /**
     * ViewHolder maps the 10 columns defined in item_attendance_row.xml
     */
    static class AttendanceViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvDay, tvIn, tvOut, tvTotalHours, tvLocation, tvDistance;
        ImageView ivFingerprint, ivGps, ivStatus;

        public AttendanceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_col_date);
            tvDay = itemView.findViewById(R.id.tv_col_day);
            tvIn = itemView.findViewById(R.id.tv_col_in);
            tvOut = itemView.findViewById(R.id.tv_col_out);
            tvTotalHours = itemView.findViewById(R.id.tv_col_hours);
            tvLocation = itemView.findViewById(R.id.tv_col_location);
            tvDistance = itemView.findViewById(R.id.tv_col_distance);
            ivFingerprint = itemView.findViewById(R.id.iv_col_fingerprint);
            ivGps = itemView.findViewById(R.id.iv_col_gps);
            ivStatus = itemView.findViewById(R.id.iv_col_status);
        }
    }
}