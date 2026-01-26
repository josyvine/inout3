package com.inout.app;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.inout.app.databinding.FragmentAdminEmployeesBinding;
import com.inout.app.models.User;
import com.inout.app.models.CompanyConfig;
import com.inout.app.adapters.EmployeeListAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Updated Fragment to handle Multi-Selection, Bulk Deletion, and Bulk Location Assignment.
 */
public class AdminEmployeesFragment extends Fragment implements EmployeeListAdapter.OnEmployeeActionListener {

    private static final String TAG = "AdminEmployeesFrag";
    private FragmentAdminEmployeesBinding binding;
    private FirebaseFirestore db;
    private EmployeeListAdapter adapter;
    private List<User> employeeList;
    private List<CompanyConfig> locationList; 

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAdminEmployeesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        employeeList = new ArrayList<>();
        locationList = new ArrayList<>();
        
        setupRecyclerView();
        listenForEmployees();
        fetchLocations(); 
    }

    private void setupRecyclerView() {
        binding.recyclerViewEmployees.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new EmployeeListAdapter(getContext(), employeeList, this);
        binding.recyclerViewEmployees.setAdapter(adapter);
    }

    private void fetchLocations() {
        db.collection("locations").addSnapshotListener((value, error) -> {
            if (error != null) return;
            if (value != null) {
                locationList.clear();
                for (DocumentSnapshot doc : value) {
                    CompanyConfig loc = doc.toObject(CompanyConfig.class);
                    if (loc != null) {
                        loc.setId(doc.getId());
                        locationList.add(loc);
                    }
                }
            }
        });
    }

    private void listenForEmployees() {
        binding.progressBar.setVisibility(View.VISIBLE);
        db.collection("users")
                .whereEqualTo("role", "employee")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                        binding.progressBar.setVisibility(View.GONE);
                        if (error != null) return;

                        if (value != null) {
                            employeeList.clear();
                            for (DocumentSnapshot doc : value) {
                                User user = doc.toObject(User.class);
                                if (user != null) {
                                    user.setUid(doc.getId());
                                    employeeList.add(user);
                                }
                            }
                            adapter.notifyDataSetChanged();
                            binding.tvEmptyView.setVisibility(employeeList.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                    }
                });
    }

    /**
     * Triggered when Admin long-presses on a selection of employees.
     */
    @Override
    public void onBulkActionRequested(List<User> selectedUsers) {
        String[] options = {"Remove Selected Employees", "Add location from saved list"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Bulk Actions (" + selectedUsers.size() + " selected)");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                showBulkDeleteConfirmation(selectedUsers);
            } else if (which == 1) {
                showBulkLocationAssignment(selectedUsers);
            }
        });
        builder.show();
    }

    private void showBulkDeleteConfirmation(List<User> selectedUsers) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Confirm Removal")
                .setMessage("Are you sure you want to remove " + selectedUsers.size() + " employees? This cannot be undone.")
                .setPositiveButton("Remove All", (dialog, which) -> {
                    performBulkDelete(selectedUsers);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performBulkDelete(List<User> selectedUsers) {
        WriteBatch batch = db.batch();
        for (User user : selectedUsers) {
            batch.delete(db.collection("users").document(user.getUid()));
        }
        batch.commit().addOnSuccessListener(aVoid -> {
            Toast.makeText(getContext(), "Selected employees removed.", Toast.LENGTH_SHORT).show();
            adapter.clearSelection();
        });
    }

    private void showBulkLocationAssignment(List<User> selectedUsers) {
        if (locationList.isEmpty()) {
            Toast.makeText(getContext(), "Add an Office Location first!", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Assign Location to " + selectedUsers.size() + " Users");

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 20);

        final Spinner spinner = new Spinner(requireContext());
        List<String> names = new ArrayList<>();
        for (CompanyConfig c : locationList) names.add(c.getName());
        
        ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, names);
        spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinAdapter);
        layout.addView(spinner);

        builder.setView(layout);
        builder.setPositiveButton("Assign and Approve", (dialog, which) -> {
            int selectedIndex = spinner.getSelectedItemPosition();
            if (selectedIndex >= 0) {
                String locId = locationList.get(selectedIndex).getId();
                performBulkAssignment(selectedUsers, locId);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void performBulkAssignment(List<User> selectedUsers, String locId) {
        WriteBatch batch = db.batch();
        for (User user : selectedUsers) {
            // Update assigned location AND set approved to true automatically
            // If they already have an ID, it remains. If not, they just need an ID assigned later or 
            // the admin can assign individual IDs in the future.
            batch.update(db.collection("users").document(user.getUid()), 
                    "assignedLocationId", locId,
                    "approved", true);
        }
        
        batch.commit().addOnSuccessListener(aVoid -> {
            Toast.makeText(getContext(), "Location assigned to selection.", Toast.LENGTH_SHORT).show();
            adapter.clearSelection();
        }).addOnFailureListener(e -> {
            Toast.makeText(getContext(), "Bulk update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}