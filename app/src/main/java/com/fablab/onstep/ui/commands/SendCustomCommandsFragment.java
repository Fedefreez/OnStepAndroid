package com.fablab.onstep.ui.commands;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.fablab.onstep.MainActivity;
import com.fablab.onstep.R;
import com.fablab.onstep.ui.bluetooth.BluetoothFragment;

public class SendCustomCommandsFragment extends Fragment {
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_send_custom_commands, container, false);

        EditText customCommandEditText = root.findViewById(R.id.customCommandEditText);

        Button sendCustomDataButton = root.findViewById(R.id.submitCustomCommandButton);
        sendCustomDataButton.setOnClickListener((v) -> {
            MainActivity.applicationLogs.add("Attempting to send: " + customCommandEditText.getText().toString());

            byte[] command = new byte[customCommandEditText.getText().toString().split(" ").length];
            int ptr = 0;
            for (String currentByte : customCommandEditText.getText().toString().split(" ")) {
                try {
                    command[ptr] = (byte) Integer.parseInt(currentByte);
                } catch (NumberFormatException e) {
                    MainActivity.createAlert("Please insert a valid number. (multiple bytes can be sent using spaces)", requireView(), true);
                }
                ptr++;
            }

            BluetoothFragment.sendData(root, command);
        });

        return root;
    }
}
