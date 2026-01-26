package com.inout.app;

import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.inout.app.databinding.FragmentEmployeeCheckinBinding;
import com.inout.app.models.AttendanceRecord;
import com.inout.app.models.CompanyConfig;
import com.inout.app.models.User;
import com.inout.app.utils.BiometricHelper;
import com.inout.app.utils.LocationHelper;
import com.inout.app.utils.TimeUtils;

/**
 * Fragment where employees perform Check-In and Check-Out.
 * FIXED: Aligned with the updated AttendanceRecord model to resolve build errors.
 */
public class EmployeeCheckInFragment extends Fragment {

    private static final String TAG = "CheckInFrag";
    private FragmentEmployeeCheckinBinding binding;
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private LocationHelper locationHelper;
    
    private User currentUser;
    private CompanyConfig assignedLocation;
    private AttendanceRecord todayRecord;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentEmployeeCheckinBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        locationHelper = new LocationHelper(requireContext());

        // UI starts in a safe disabled state until data loads
        binding.btnCheckIn.setEnabled(false);
        binding.btnCheckOut.setEnabled(false);

        loadUserDataAndStatus();

        binding.btnCheckIn.setOnClickListener(v -> initiateAction(true));
        binding.btnCheckOut.setOnClickListener(v -> initiateAction(false));
    }

    /**
     * Fetches real user profile data to display on the header cards.
     */
    private void loadUserDataAndStatus() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();
        
        db.collection("users").document(uid).addSnapshotListener((doc, error) -> {
            if (error != null || doc == null || !doc.exists()) return;

            currentUser = doc.toObject(User.class);
            if (currentUser != null) {
                // Update UI Header with live data
                binding.tvEmployeeName.setText(currentUser.getName() != null ? currentUser.getName() : "Unknown User");
                binding.tvEmployeeId.setText(currentUser.getEmployeeId() != null ? currentUser.getEmployeeId() : "Pending ID");

                // Load the office location assigned to this user
                if (currentUser.getAssignedLocationId() != null && !currentUser.getAssignedLocationId().isEmpty()) {
                    fetchAssignedLocationDetails(currentUser.getAssignedLocationId());
                } else {
                    binding.tvStatus.setText("Status: No workplace assigned.");
                }
                
                loadTodayAttendance();
            }
        });
    }

    private void fetchAssignedLocationDetails(String locId) {
        db.collection("locations").document(locId).get().addOnSuccessListener(doc -> {
            assignedLocation = doc.toObject(CompanyConfig.class);
            updateUIBasedOnStatus();
        });
    }

    private void loadTodayAttendance() {
        if (currentUser == null || currentUser.getEmployeeId() == null) return;
        
        String dateId = TimeUtils.getCurrentDateId();
        String recordId = currentUser.getEmployeeId() + "_" + dateId;

        db.collection("attendance").document(recordId).addSnapshotListener((snapshot, e) -> {
            if (snapshot != null && snapshot.exists()) {
                todayRecord = snapshot.toObject(AttendanceRecord.class);
            } else {
                todayRecord = null;
            }
            updateUIBasedOnStatus();
        });
    }

    private void updateUIBasedOnStatus() {
        if (currentUser == null || assignedLocation == null) return;

        if (todayRecord == null) {
            binding.btnCheckIn.setEnabled(true);
            binding.btnCheckOut.setEnabled(false);
            binding.tvStatus.setText("Status: Ready to Check-In (" + assignedLocation.getName() + ")");
        } else if (todayRecord.getCheckOutTime() == null || todayRecord.getCheckOutTime().isEmpty()) {
            binding.btnCheckIn.setEnabled(false);
            binding.btnCheckOut.setEnabled(true);
            binding.tvStatus.setText("Status: Checked In at " + todayRecord.getCheckInTime());
        } else {
            binding.btnCheckIn.setEnabled(false);
            binding.btnCheckOut.setEnabled(false);
            binding.tvStatus.setText("Status: Day Completed (" + todayRecord.getTotalHours() + ")");
        }
    }

    private void initiateAction(boolean isCheckIn) {
        if (assignedLocation == null) {
            Toast.makeText(getContext(), "Error: Office location not assigned.", Toast.LENGTH_LONG).show();
            return;
        }

        BiometricHelper.authenticate(requireActivity(), new BiometricHelper.BiometricCallback() {
            @Override
            public void onAuthenticationSuccess() {
                verifyLocationAndProceed(isCheckIn);
            }

            @Override
            public void onAuthenticationError(String errorMsg) {
                Toast.makeText(getContext(), "Auth Error: " + errorMsg, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationFailed() {
                Toast.makeText(getContext(), "Fingerprint verification failed.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void verifyLocationAndProceed(boolean isCheckIn) {
        binding.progressBar.setVisibility(View.VISIBLE);
        
        locationHelper.getCurrentLocation(new LocationHelper.LocationResultCallback() {
            @Override
            public void onLocationResult(Location location) {
                binding.progressBar.setVisibility(View.GONE);
                
                if (location != null) {
                    boolean inRange = LocationHelper.isWithinRadius(
                            location.getLatitude(), location.getLongitude(),
                            assignedLocation.getLatitude(), assignedLocation.getLongitude(),
                            assignedLocation.getRadius());

                    if (inRange) {
                        float dist = LocationHelper.calculateDistance(
                                location.getLatitude(), location.getLongitude(),
                                assignedLocation.getLatitude(), assignedLocation.getLongitude());
                        
                        if (isCheckIn) performCheckIn(location, dist);
                        else performCheckOut(location);
                    } else {
                        Toast.makeText(getContext(), "Access Denied: Not within office range.", Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onError(String errorMsg) {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(getContext(), "GPS Error: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performCheckIn(Location loc, float distance) {
        String dateId = TimeUtils.getCurrentDateId();
        String recordId = currentUser.getEmployeeId() + "_" + dateId;

        // FIXED: Using the 4-argument constructor restored in AttendanceRecord model
        AttendanceRecord record = new AttendanceRecord(
                currentUser.getEmployeeId(), 
                currentUser.getName(), 
                dateId, 
                TimeUtils.getCurrentTimestamp());

        record.setRecordId(recordId);
        record.setCheckInTime(TimeUtils.getCurrentTime());
        record.setCheckInLat(loc.getLatitude());
        record.setCheckInLng(loc.getLongitude());
        record.setFingerprintVerified(true);
        record.setLocationVerified(true); // Maps to setGpsVerified in model
        record.setDistanceMeters(distance);
        record.setLocationName(assignedLocation.getName());

        db.collection("attendance").document(recordId).set(record)
                .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Check-In Success!", Toast.LENGTH_SHORT).show());
    }

    private void performCheckOut(Location loc) {
        if (todayRecord == null) return;

        String checkOutTime = TimeUtils.getCurrentTime();
        String totalHrs = TimeUtils.calculateDuration(todayRecord.getCheckInTime(), checkOutTime);

        db.collection("attendance").document(todayRecord.getRecordId())
                .update(
                        "checkOutTime", checkOutTime,
                        "checkOutLat", loc.getLatitude(),
                        "checkOutLng", loc.getLongitude(),
                        "totalHours", totalHrs
                )
                .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Check-Out Success!", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}