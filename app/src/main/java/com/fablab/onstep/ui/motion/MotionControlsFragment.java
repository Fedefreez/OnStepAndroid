package com.fablab.onstep.ui.motion;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.fablab.onstep.MainActivity;
import com.fablab.onstep.R;
import com.fablab.onstep.ui.bluetooth.BluetoothFragment;
import com.fablab.onstep.ui.options.OptionsFragment;

public class MotionControlsFragment extends Fragment {
    private View hostFragment;

    private static final String MOVE_NORTH = ":Mn#";
    private static final String STOP_NORTH = ":Qn#";
    private static final String MOVE_EAST = ":Me#";
    private static final String STOP_EAST = ":Qe#";
    private static final String MOVE_WEST = ":Mw#";
    private static final String STOP_WEST = ":Qe#";
    private static final String MOVE_SOUTH = ":Ms#";
    private static final String STOP_SOUTH = ":Qs#";

    private int currentMotorSpeed;
    private boolean toggleTracking;

    public MotionControlsFragment(View hostFragment) {
        this.hostFragment = hostFragment;
    }

    @SuppressLint("ClickableViewAccessibility") //app not for blind people
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_motion_controls, container, false);

        MainActivity.toggleTouchEvents((ViewGroup) root, BluetoothFragment.connected);

        currentMotorSpeed = OptionsFragment.getPreferencesInt("motorSpeed", hostFragment.getContext());
        TextView currentMotorSpeedTextView = root.findViewById(R.id.currentMotorSpeedTextView);
        currentMotorSpeedTextView.setText(getString(R.string.empty_int, currentMotorSpeed));

        Button toggleTrackingButton = root.findViewById(R.id.toggleTrackingButton);
        toggleTrackingButton.setOnClickListener((v) -> {
            BluetoothFragment.sendData(hostFragment, (toggleTracking ? ":Te#" : ":Td#").getBytes());
            toggleTracking = !toggleTracking;
            toggleTrackingButton.setText(toggleTracking ? "Enable tracking" : "Disable tracking");
        });

        ImageView northButton = root.findViewById(R.id.moveNorthImageButton);
        northButton.setOnTouchListener((view, motionEvent) -> {
            if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                BluetoothFragment.sendData(hostFragment, MOVE_NORTH.getBytes());
            } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                BluetoothFragment.sendData(hostFragment, STOP_NORTH.getBytes());
            }

            return true;
        });

        ImageView eastButton = root.findViewById(R.id.moveEastImageButton);
        eastButton.setOnTouchListener((view, motionEvent) -> {
            if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                BluetoothFragment.sendData(hostFragment, MOVE_EAST.getBytes());
            } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                BluetoothFragment.sendData(hostFragment, STOP_EAST.getBytes());
            }

            return true;
        });

        ImageView westButton = root.findViewById(R.id.moveWestImageButton);
        westButton.setOnTouchListener((view, motionEvent) -> {
            if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                BluetoothFragment.sendData(hostFragment, MOVE_WEST.getBytes());
            } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                BluetoothFragment.sendData(hostFragment, STOP_WEST.getBytes());
            }

            return true;
        });

        ImageView southButton = root.findViewById(R.id.moveSouthImageButton);
        southButton.setOnTouchListener((view, motionEvent) -> {
            if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                BluetoothFragment.sendData(hostFragment, MOVE_SOUTH.getBytes());
            } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                BluetoothFragment.sendData(hostFragment, STOP_SOUTH.getBytes());
            }

            return true;
        });

        Button increaseMotorSpeedButton = root.findViewById(R.id.increaseMotorSpeedButton);
        increaseMotorSpeedButton.setOnClickListener((v) -> {
            if (currentMotorSpeed >= 9) {
                MainActivity.createAlert("Maximum speed reached.", hostFragment, true);
                return;
            }

            currentMotorSpeed++;
            updateMotorSpeed();
        });

        Button decreaseMotorSpeedButton = root.findViewById(R.id.decreaseMotorSpeedButton);
        decreaseMotorSpeedButton.setOnClickListener((v) -> {
            if (currentMotorSpeed <= 0) {
                MainActivity.createAlert("Minimum speed reached.", hostFragment, true);
                return;
            }

            currentMotorSpeed--;
            updateMotorSpeed();
        });

        return root;
    }

    private void updateMotorSpeed() {
        MainActivity.applicationLogs.add("Updating motor speed with speed " + currentMotorSpeed);
        BluetoothFragment.sendData(hostFragment, getString(R.string.set_motor_speed, currentMotorSpeed).getBytes());

        TextView currentMotorSpeedTextView = requireView().findViewById(R.id.currentMotorSpeedTextView);
        currentMotorSpeedTextView.setText(getString(R.string.empty_int, currentMotorSpeed));

        OptionsFragment.setPreferencesInt("motorSpeed", currentMotorSpeed, hostFragment.getContext());
    }
}
