package com.inout.app.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.inout.app.R;
import com.inout.app.models.User;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Updated Adapter to handle Multi-Selection and Bulk Actions.
 */
public class EmployeeListAdapter extends RecyclerView.Adapter<EmployeeListAdapter.EmployeeViewHolder> {

    private final Context context;
    private final List<User> employeeList;
    private final OnEmployeeActionListener listener;
    
    // Set to store the UIDs of selected employees
    private final Set<String> selectedUserIds = new HashSet<>();

    public interface OnEmployeeActionListener {
        // Triggered on long press when employees are selected
        void onBulkActionRequested(List<User> selectedUsers);
    }

    public EmployeeListAdapter(Context context, List<User> employeeList, OnEmployeeActionListener listener) {
        this.context = context;
        this.employeeList = employeeList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public EmployeeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_employee, parent, false);
        return new EmployeeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EmployeeViewHolder holder, int position) {
        User user = employeeList.get(position);

        holder.tvName.setText(user.getName());
        holder.tvPhone.setText(user.getPhone() != null ? user.getPhone() : "No Phone");
        
        // Show status and assigned location ID if exists
        String statusText = user.isApproved() ? "Approved" : "Pending";
        if (user.getEmployeeId() != null) {
            statusText += " (" + user.getEmployeeId() + ")";
        }
        holder.tvStatus.setText("Status: " + statusText);
        
        // Visual feedback for selection
        if (selectedUserIds.contains(user.getUid())) {
            holder.viewOverlay.setVisibility(View.VISIBLE);
            holder.ivCheck.setVisibility(View.VISIBLE);
        } else {
            holder.viewOverlay.setVisibility(View.GONE);
            holder.ivCheck.setVisibility(View.GONE);
        }

        // Logic for Profile Photo (Standard placeholder)
        holder.ivProfile.setImageResource(R.drawable.inout); 

        // SINGLE TAP: Toggle Selection
        holder.itemView.setOnClickListener(v -> {
            toggleSelection(user.getUid());
        });

        // LONG PRESS: Trigger the action menu (Remove/Assign Location)
        holder.itemView.setOnLongClickListener(v -> {
            if (!selectedUserIds.isEmpty()) {
                // Ensure the long-pressed item is part of the selection
                if (!selectedUserIds.contains(user.getUid())) {
                    toggleSelection(user.getUid());
                }
                
                if (listener != null) {
                    listener.onBulkActionRequested(getSelectedUsers());
                }
                return true;
            }
            return false;
        });
    }

    private void toggleSelection(String uid) {
        if (selectedUserIds.contains(uid)) {
            selectedUserIds.remove(uid);
        } else {
            selectedUserIds.add(uid);
        }
        notifyDataSetChanged();
    }

    /**
     * @return List of User objects currently selected by the Admin.
     */
    public List<User> getSelectedUsers() {
        List<User> selectedUsers = new ArrayList<>();
        for (User user : employeeList) {
            if (selectedUserIds.contains(user.getUid())) {
                selectedUsers.add(user);
            }
        }
        return selectedUsers;
    }

    public void clearSelection() {
        selectedUserIds.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return employeeList.size();
    }

    static class EmployeeViewHolder extends RecyclerView.ViewHolder {
        ImageView ivProfile, ivCheck;
        TextView tvName, tvPhone, tvStatus;
        View viewOverlay;

        public EmployeeViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProfile = itemView.findViewById(R.id.iv_employee_photo);
            ivCheck = itemView.findViewById(R.id.iv_select_check);
            tvName = itemView.findViewById(R.id.tv_employee_name);
            tvPhone = itemView.findViewById(R.id.tv_employee_phone);
            tvStatus = itemView.findViewById(R.id.tv_employee_status);
            viewOverlay = itemView.findViewById(R.id.view_selected_overlay);
        }
    }
}