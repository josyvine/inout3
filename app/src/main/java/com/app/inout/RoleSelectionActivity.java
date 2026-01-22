package com.inout.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.inout.app.databinding.ActivityRoleSelectionBinding;
import com.inout.app.utils.EncryptionHelper;

public class RoleSelectionActivity extends AppCompatActivity {

    private ActivityRoleSelectionBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRoleSelectionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnRoleAdmin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectRole("admin");
            }
        });

        binding.btnRoleEmployee.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectRole("employee");
            }
        });
    }

    private void selectRole(String role) {
        // 1. Save the selected role securely
        EncryptionHelper.getInstance(this).saveUserRole(role);

        // 2. Navigate based on role
        if ("admin".equals(role)) {
            // Admin needs to upload Firebase Config first
            startActivity(new Intent(this, AdminSetupActivity.class));
        } else {
            // Employee needs to scan the Admin's QR code first
            startActivity(new Intent(this, EmployeeQrScanActivity.class));
        }
        
        // 3. Close this activity so they can't go back to change role easily
        finish();
    }
}