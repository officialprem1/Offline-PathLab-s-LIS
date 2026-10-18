
package com.pathlis.offline;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private int dp(float value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(248, 245, 255));

        TextView title = new TextView(this);
        title.setText("Path LIS");
        title.setTextSize(30);
        title.setTextColor(Color.rgb(105, 65, 180));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));

        TextView subtitle = new TextView(this);
        subtitle.setText("Laboratory Information System");
        subtitle.setTextSize(16);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(45)));

        Button register = new Button(this);
        register.setText("Patient Registration");
        root.addView(register, buttonParams());

        Button reports = new Button(this);
        reports.setText("Laboratory Reports");
        root.addView(reports, buttonParams());

        Button settings = new Button(this);
        settings.setText("Lab Settings");
        root.addView(settings, buttonParams());

        TextView status = new TextView(this);
        status.setText(
                "Offline-ready app foundation\n" +
                "Patient database will be added next."
        );
        status.setTextSize(14);
        status.setTextColor(Color.DKGRAY);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(100)));

        setContentView(root);
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        params.setMargins(0, dp(10), 0, dp(10));
        return params;
    }
}
