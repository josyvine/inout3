package com.inout.app.adapters;

import android.content.Context;
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

import java.util.List;

/**
 * Adapter for the Admin to view and manage the list of Employees.
 */
public class EmployeeListAdapter extends RecyclerView.Adapter<EmployeeListAdapter.EmployeeViewHolder> {

    private final Context context;
    private final List<User> employeeList;
    private final OnEmployeeActionListener listener;

    /**
     * Interface to handle actions from the Admin Dashboard.
     */
    public interface OnEmployeeActionListener {
        void onApproveClicked(User user);
        void onDeleteClicked(User user);
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
        
        // Show Employee ID if approved, otherwise show "Pending"
        if (user.isApproved()) {
            holder.tvStatus.setText("Status: Approved (" + user.getEmployeeId() + ")");
            holder.tvStatus.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
            holder.btnApprove.setVisibility(View.GONE);
        } else {
            holder.tvStatus.setText("Status: Pending Approval");
            holder.tvStatus.setTextColor(context.getResources().getColor(android.R.color.holo_orange_dark));
            holder.btnApprove.setVisibility(View.VISIBLE);
        }

        // Logic for Profile Photo (In a full app, use Glide/Picasso to load user.getPhotoUrl())
        // For now, we use the icon placeholder.
        holder.ivProfile.setImageResource(R.drawable.inout); 

        // Action Listeners
        holder.btnApprove.setOnClickListener(v -> {
            if (listener != null) listener.onApproveClicked(user);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onDeleteClicked(user);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return employeeList.size();
    }

    static class EmployeeViewHolder extends RecyclerView.ViewHolder {
        ImageView ivProfile;
        TextView tvName, tvPhone, tvStatus;
        Button btnApprove;

        public EmployeeViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProfile = itemView.findViewById(R.id.iv_employee_photo);
            tvName = itemView.findViewById(R.id.tv_employee_name);
            tvPhone = itemView.findViewById(R.id.tv_employee_phone);
            tvStatus = itemView.findViewById(R.id.tv_employee_status);
            btnApprove = itemView.findViewById(R.id.btn_approve_employee);
        }
    }
}