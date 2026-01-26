package com.inout.app;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.inout.app.adapters.AttendanceAdapter;
import com.inout.app.databinding.DialogAttendanceProfileBinding;
import com.inout.app.models.AttendanceRecord;
import com.inout.app.models.User;
import com.inout.app.utils.EncryptionHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Professional Pop-up Window for Attendance Profile.
 * Features: Fixed CV-Header, Horizontal CSV Table, Full Month Report.
 */
public class AttendanceProfileDialog extends DialogFragment {

    private static final String TAG = "AttendanceDialog";
    private DialogAttendanceProfileBinding binding;
    private User employee;
    private FirebaseFirestore db;
    private AttendanceAdapter adapter;
    private List<AttendanceRecord> fullMonthList;

    public static AttendanceProfileDialog newInstance(User user) {
        AttendanceProfileDialog frag = new AttendanceProfileDialog();
        frag.employee = user;
        return frag;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            // Make the dialog full screen width/height
            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            int height = ViewGroup.LayoutParams.MATCH_PARENT;
            dialog.getWindow().setLayout(width, height);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogAttendanceProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = FirebaseFirestore.getInstance();
        fullMonthList = new ArrayList<>();

        setupHeader();
        setupTable();
        loadAttendanceData();

        binding.btnClose.setOnClickListener(v -> dismiss());
        binding.btnExportCsv.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Preparing CSV for " + employee.getName(), Toast.LENGTH_SHORT).show();
            // CsvExportHelper will be called here in the next steps
        });
    }

    /**
     * Fills the fixed CV-style header with employee information and Google photo.
     */
    private void setupHeader() {
        binding.tvHeaderName.setText(employee.getName());
        binding.tvHeaderId.setText("ID: " + employee.getEmployeeId());
        binding.tvHeaderPhone.setText("Phone: " + employee.getPhone());
        binding.tvHeaderCompany.setText(EncryptionHelper.getInstance(getContext()).getCompanyName());

        // Set the current Month & Year for the report header
        Calendar cal = Calendar.getInstance();
        String currentMonthYear = new SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.getTime());
        binding.tvHeaderMonth.setText(currentMonthYear);

        // Load Circular Google Profile Image
        if (employee.getPhotoUrl() != null && !employee.getPhotoUrl().isEmpty()) {
            Glide.with(this)
                    .load(employee.getPhotoUrl())
                    .circleCrop()
                    .placeholder(R.drawable.inout)
                    .into(binding.ivProfilePhoto);
        }
    }

    private void setupTable() {
        binding.rvAttendanceTable.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AttendanceAdapter(fullMonthList);
        binding.rvAttendanceTable.setAdapter(adapter);
    }

    /**
     * Logic to fetch real logs and generate "Absent" rows for the rest of the month.
     */
    private void loadAttendanceData() {
        binding.progressBar.setVisibility(View.VISIBLE);

        db.collection("attendance")
                .whereEqualTo("employeeId", employee.getEmployeeId())
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Map<String, AttendanceRecord> existingLogs = new HashMap<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        AttendanceRecord record = doc.toObject(AttendanceRecord.class);
                        if (record != null) {
                            existingLogs.put(record.getDate(), record);
                        }
                    }
                    generateFullMonthReport(existingLogs);
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Log.e(TAG, "Data fetch failed", e);
                    Toast.makeText(getContext(), "Error loading month records", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Iterates through every day of the current month.
     * If a record exists in Firestore, it adds it. Otherwise, it creates an Absent row.
     */
    private void generateFullMonthReport(Map<String, AttendanceRecord> logs) {
        fullMonthList.clear();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        SimpleDateFormat dateIdFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat dayNameFormat = new SimpleDateFormat("EEEE", Locale.US);

        for (int i = 1; i <= maxDay; i++) {
            String dateId = dateIdFormat.format(cal.getTime());
            String dayName = dayNameFormat.format(cal.getTime());

            if (logs.containsKey(dateId)) {
                AttendanceRecord record = logs.get(dateId);
                record.setDayOfWeek(dayName); // Ensure day is set
                fullMonthList.add(record);
            } else {
                // Create dummy Absent record
                AttendanceRecord absent = new AttendanceRecord();
                absent.setDate(dateId);
                absent.setDayOfWeek(dayName);
                // Status helper in model will handle the "Absent" icon based on null fields
                fullMonthList.add(absent);
            }
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        binding.progressBar.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();
    }
}