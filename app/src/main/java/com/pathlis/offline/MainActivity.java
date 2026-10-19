
package com.pathlis.offline;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private LinearLayout root;
    private SupabaseClient cloud;
    private DB db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String selectedPatientId = "";
    private String selectedPatientName = "";

    private int purple = Color.rgb(111, 66, 193);
    private int dark = Color.rgb(35, 35, 45);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cloud = new SupabaseClient();
        db = new DB(this);

        loginScreen();
    }

    private void base(String heading) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 22, 22, 22);
        root.setBackgroundColor(Color.rgb(249, 247, 255));

        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText(heading);
        title.setTextSize(25);
        title.setTextColor(purple);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(0, 8, 0, 18);
        title.setTypeface(null, 1);
        root.addView(title);
    }

    private void add(View view) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        p.setMargins(0, 5, 0, 9);
        root.addView(view, p);
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(16);
        e.setSingleLine(false);
        e.setPadding(14, 10, 14, 10);
        e.setBackgroundColor(Color.WHITE);
        return e;
    }

    private Button btn(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(purple)
        );
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTextColor(dark);
        t.setPadding(0, 8, 0, 4);
        add(t);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private boolean isAdmin() {
        return "admin".equals(cloud.role);
    }

    private String pref(String key, String fallback) {
        return getSharedPreferences("lab", MODE_PRIVATE)
                .getString(key, fallback);
    }

    private void loginScreen() {
        base("Path LIS");

        label("Laboratory Information System");
        EditText email = field("Registered email");
        email.setSingleLine(true);

        EditText password = field("Password");
        password.setSingleLine(true);
        password.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        add(email);
        add(password);

        add(btn("Login", () -> {
            String em = email.getText().toString().trim();
            String pw = password.getText().toString();

            if (em.isEmpty() || pw.isEmpty()) {
                toast("Email aur password enter karo");
                return;
            }

            executor.execute(() -> {
                try {
                    cloud.login(em, pw);

                    runOnUiThread(() -> {
                        toast("Welcome " + cloud.fullName);
                        home();
                    });

                } catch (Exception ex) {
                    String message = ex.getMessage();
                    runOnUiThread(() ->
                            toast(message == null ? "Login failed" : message)
                    );
                }
            });
        }));

        label("Use your existing Supabase Auth account.");
    }

    private void home() {
        base("Path LIS");

        label("User: " + cloud.fullName);
        label("Role: " + cloud.role);
        label("Online laboratory system");

        add(btn("Register Patient", this::patientForm));
        add(btn("View / Sync Patients", this::patientsScreen));
        add(btn("Create Report", this::reportForm));
        add(btn("View Reports", this::reportsScreen));

        if (isAdmin()) {
            add(btn("Lab Settings", this::settings));
        }

        add(btn("Refresh Cloud Data", this::syncCloud));

        add(btn("Logout", () -> {
            cloud.clearSession();
            loginScreen();
        }));
    }

    private void syncCloud() {
        executor.execute(() -> {
            try {
                JSONArray patients = cloud.getPatients();
                JSONArray reports = cloud.getReports();

                db.importPatients(patients, cloud.labId);
                db.importReports(reports, cloud.labId);

                runOnUiThread(() -> {
                    toast("Cloud data synced");
                    home();
                });

            } catch (Exception ex) {
                String message = ex.getMessage();
                runOnUiThread(() ->
                        toast(message == null ? "Sync failed" : message)
                );
            }
        });
    }

    private void patientForm() {
        base("Register Patient");

        EditText name = field("Patient name");
        EditText age = field("Age");
        EditText sex = field("Sex");
        EditText phone = field("Phone");
        EditText doctor = field("Referred by doctor");

        add(name);
        add(age);
        add(sex);
        add(phone);
        add(doctor);

        add(btn("Save Patient", () -> {
            String n = name.getText().toString().trim();
            String a = age.getText().toString().trim();
            String s = sex.getText().toString().trim();
            String ph = phone.getText().toString().trim();
            String d = doctor.getText().toString().trim();

            if (n.isEmpty() || a.isEmpty() || s.isEmpty()) {
                toast("Name, age aur sex required hain");
                return;
            }

            executor.execute(() -> {
                try {
                    JSONObject remote = cloud.insertPatient(n, a, s, ph, d);
                    String remoteId = remote.optString("id", "");

                    db.savePatient(
                            remoteId, cloud.labId, n, a, s, ph, d,
                            remote.optString("created_at", now())
                    );

                    runOnUiThread(() -> {
                        toast("Patient saved successfully");
                        patientsScreen();
                    });

                } catch (Exception ex) {
                    String message = ex.getMessage();
                    runOnUiThread(() ->
                            toast(message == null ? "Patient save failed" : message)
                    );
                }
            });
        }));

        add(btn("Back", this::home));
    }

    private void patientsScreen() {
        base("Patients");

        add(btn("Sync from Cloud", this::syncCloud));
        ArrayList<Patient> list = db.allPatients();

        if (list.isEmpty()) {
            label("No patients found. Sync or register a patient.");
        }

        for (Patient p : list) {
            label(
                    p.name + "\nAge: " + p.age +
                    " | Sex: " + p.sex +
                    "\nPhone: " + safe(p.phone) +
                    "\nLocal ID: " + p.id +
                    "\nCloud ID: " + safe(p.remoteId)
            );

            add(btn("Create Report for " + p.name, () -> {
                selectedPatientId = String.valueOf(p.id);
                selectedPatientName = p.name;
                reportForm();
            }));
        }

        add(btn("Back", this::home));
    }

    private void reportForm() {
        base("Create Lab Report");

        EditText patientId = field("Local patient ID");
        if (!selectedPatientId.isEmpty()) {
            patientId.setText(selectedPatientId);
        }
        add(patientId);

        EditText patientName = field("Patient name (for display)");
        patientName.setText(selectedPatientName);
        add(patientName);

        EditText test = field("Test name (e.g. CBC, LFT, KFT)");
        add(test);

        EditText result = field(
                "Result details: parameter, value, unit and reference range"
        );
        result.setMinLines(5);
        result.setGravity(Gravity.TOP);
        add(result);

        label("Enter verified results and reference ranges carefully.");

        add(btn("Save Report and Create PDF", () -> {
            String idText = patientId.getText().toString().trim();
            String patient = patientName.getText().toString().trim();
            String testName = test.getText().toString().trim();
            String resultText = result.getText().toString().trim();

            if (idText.isEmpty() || patient.isEmpty()
                    || testName.isEmpty() || resultText.isEmpty()) {
                toast("Sabhi report fields fill karo");
                return;
            }

            long localId;
            try {
                localId = Long.parseLong(idText);
            } catch (Exception ex) {
                toast("Valid local patient ID enter karo");
                return;
            }

            Patient p = db.patient(localId);
            if (p == null) {
                toast("Patient nahi mila. Patient list se select karo.");
                return;
            }

            executor.execute(() -> {
                try {
                    JSONObject results = new JSONObject();
                    results.put("result_text", resultText);

                    JSONObject remote = cloud.insertReport(
                            p.remoteId, testName, results
                    );

                    long reportId = db.saveReport(
                            remote.optString("id", ""),
                            cloud.labId,
                            p.remoteId,
                            localId,
                            testName,
                            results.toString(),
                            remote.optString("created_at", now())
                    );

                    runOnUiThread(() -> {
                        try {
                            Uri uri = createPdf(reportId, p, testName, resultText);
                            if (uri != null) {
                                toast("Report saved. PDF ready to share.");
                                sharePdf(uri);
                            } else {
                                toast("Report saved, but PDF creation failed.");
                            }
                            home();
                        } catch (Exception ex) {
                            toast("PDF error: " + ex.getMessage());
                        }
                    });

                } catch (Exception ex) {
                    String message = ex.getMessage();
                    runOnUiThread(() ->
                            toast(message == null ? "Report save failed" : message)
                    );
                }
            });
        }));

        add(btn("Back", this::home));
    }

    private Uri createPdf(
            long reportId, Patient patient, String test, String resultText
    ) throws Exception {

        PdfDocument pdf = new PdfDocument();
        PdfDocument.PageInfo info =
                new PdfDocument.PageInfo.Builder(595, 842, 1).create();

        PdfDocument.Page page = pdf.startPage(info);
        android.graphics.Canvas canvas = page.getCanvas();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        paint.setColor(Color.rgb(80, 50, 145));
        paint.setTextSize(23);
        paint.setFakeBoldText(true);
        canvas.drawText(pref("lab_name", "Maa PathLab"), 40, 55, paint);

        paint.setFakeBoldText(false);
        paint.setTextSize(12);
        paint.setColor(Color.BLACK);

        canvas.drawText(pref("lab_address", ""), 40, 78, paint);
        canvas.drawLine(40, 95, 555, 95, paint);

        paint.setTextSize(18);
        paint.setFakeBoldText(true);
        canvas.drawText("LABORATORY REPORT", 40, 125, paint);

        paint.setFakeBoldText(false);
        paint.setTextSize(12);

        canvas.drawText("Report ID: " + reportId, 40, 155, paint);
        canvas.drawText("Date: " + now(), 40, 175, paint);
        canvas.drawText("Patient: " + patient.name, 40, 210, paint);
        canvas.drawText("Age: " + safe(patient.age), 40, 230, paint);
        canvas.drawText("Sex: " + safe(patient.sex), 40, 250, paint);
        canvas.drawText("Phone: " + safe(patient.phone), 40, 270, paint);
        canvas.drawText("Referred by: " + safe(patient.doctor), 40, 290, paint);

        canvas.drawLine(40, 315, 555, 315, paint);

        paint.setFakeBoldText(true);
        canvas.drawText("Test: " + test, 40, 345, paint);
        paint.setFakeBoldText(false);

        int y = 375;
        for (String line : resultText.split("\\n")) {
            if (y > 690) break;
            canvas.drawText(line, 40, y, paint);
            y += 22;
        }

        canvas.drawLine(40, 745, 555, 745, paint);
        canvas.drawText(
                "Technologist: " + pref("tech_name", ""),
                40, 770, paint
        );
        canvas.drawText(
                "Designation: " + pref("tech_designation", "Lab Technician"),
                40, 790, paint
        );

        String pathologist = pref("path_name", "");
        if (!pathologist.isEmpty()) {
            canvas.drawText(
                    "Pathologist: " + pathologist,
                    320, 770, paint
            );
            canvas.drawText(
                    "Designation: " + pref("path_designation", "Pathologist"),
                    320, 790, paint
            );
        }

        pdf.finishPage(page);

        Uri uri = null;
        String filename = "PathLIS_Report_" + reportId + ".pdf";

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "Download/PathLIS"
                );

                uri = getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                );

                if (uri == null) {
                    throw new Exception("Downloads location unavailable");
                }

                try (OutputStream out = getContentResolver()
                        .openOutputStream(uri)) {
                    if (out == null) throw new Exception("Cannot open PDF output");
                    pdf.writeTo(out);
                }

            } else {
                java.io.File folder = new java.io.File(
                        getExternalFilesDir(null), "Reports"
                );
                if (!folder.exists() && !folder.mkdirs()) {
                    throw new Exception("Cannot create report folder");
                }

                java.io.File file = new java.io.File(folder, filename);
                try (OutputStream out = new java.io.FileOutputStream(file)) {
                    pdf.writeTo(out);
                }
                toast("PDF saved in app Reports folder on this device.");
            }

            db.markReportPdf(reportId, uri == null ? "" : uri.toString());

        } finally {
            pdf.close();
        }

        return uri;
    }

    private void sharePdf(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share laboratory report"));
    }

    private void reportsScreen() {
        base("Reports");

        ArrayList<Report> list = db.allReports();

        if (list.isEmpty()) {
            label("No reports found. Sync cloud data or create a report.");
        }

        for (Report r : list) {
            label(
                    "Report ID: " + r.id +
                    "\nTest: " + r.test +
                    "\nDate: " + safe(r.created) +
                    "\nPatient local ID: " + r.patientId
            );

            if (r.pdfPath != null && !r.pdfPath.isEmpty()) {
                add(btn("Share saved PDF", () -> {
                    try {
                        sharePdf(Uri.parse(r.pdfPath));
                    } catch (Exception ex) {
                        toast("Unable to share saved PDF");
                    }
                }));
            }
        }

        add(btn("Back", this::home));
    }

    private void settings() {
        if (!isAdmin()) {
            toast("Only Admin can change lab settings");
            home();
            return;
        }

        base("Lab Settings");

        EditText lab = field("Laboratory name");
        lab.setText(pref("lab_name", "Maa PathLab"));

        EditText addr = field("Laboratory address");
        addr.setText(pref("lab_address", ""));

        EditText tech = field("Technologist name");
        tech.setText(pref("tech_name", ""));

        EditText td = field("Technologist designation");
        td.setText(pref("tech_designation", "Lab Technician"));

        EditText path = field("Pathologist name (optional)");
        path.setText(pref("path_name", ""));

        EditText pd = field("Pathologist designation");
        pd.setText(pref("path_designation", "Pathologist"));

        add(lab);
        add(addr);
        add(tech);
        add(td);
        add(path);
        add(pd);

        add(btn("Save Settings", () -> {
            getSharedPreferences("lab", MODE_PRIVATE).edit()
                    .putString("lab_name", lab.getText().toString().trim())
                    .putString("lab_address", addr.getText().toString().trim())
                    .putString("tech_name", tech.getText().toString().trim())
                    .putString("tech_designation", td.getText().toString().trim())
                    .putString("path_name", path.getText().toString().trim())
                    .putString("path_designation", pd.getText().toString().trim())
                    .apply();

                    toast("Lab settings saved on this device");
            home();
        }));

        add(btn("Back", this::home));
    }

    private String now() {
        return new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault()
        ).format(new Date());
    }

    private String safe(String value) {
        return value == null || "null".equals(value) ? "" : value;
    }    private static class Patient {
        long id;
        String remoteId, labId, name, age, sex, phone, doctor, date;
    }

    private static class Report {
        long id, patientId;
        String remoteId, labId, test, results, created, pdfPath;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        if (db != null) db.close();
    }    private class DB extends SQLiteOpenHelper {

        DB(Context context) {
            super(context, "pathlis.db", null, 3);
        }

        @Override
        public void onCreate(SQLiteDatabase d) {
            d.execSQL(
                "CREATE TABLE patients (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "remote_id TEXT, lab_id TEXT, name TEXT, age TEXT," +
                "sex TEXT, phone TEXT, doctor TEXT, date TEXT)"
            );

            d.execSQL(
                "CREATE TABLE reports (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "remote_id TEXT, lab_id TEXT, patient_id INTEGER," +
                "test TEXT, results TEXT, created TEXT, pdf_path TEXT)"
            );
        }

        @Override
        public void onUpgrade(
                SQLiteDatabase d, int oldVersion, int newVersion) {
            addColumn(d, "patients", "remote_id", "TEXT");
            addColumn(d, "patients", "lab_id", "TEXT");
            addColumn(d, "patients", "name", "TEXT");
            addColumn(d, "patients", "age", "TEXT");
            addColumn(d, "patients", "sex", "TEXT");
            addColumn(d, "patients", "phone", "TEXT");
            addColumn(d, "patients", "doctor", "TEXT");
            addColumn(d, "patients", "date", "TEXT");

            addColumn(d, "reports", "remote_id", "TEXT");
            addColumn(d, "reports", "lab_id", "TEXT");
            addColumn(d, "reports", "patient_id", "INTEGER");
            addColumn(d, "reports", "test", "TEXT");
            addColumn(d, "reports", "results", "TEXT");
            addColumn(d, "reports", "created", "TEXT");
            addColumn(d, "reports", "pdf_path", "TEXT");
        }

        private void addColumn(
                SQLiteDatabase d, String table,
                String column, String type) {
            try {
                d.execSQL("ALTER TABLE " + table +
                        " ADD COLUMN " + column + " " + type);
            } catch (Exception ignored) {
            }
        }

        void savePatient(
                String remoteId, String labId, String name, String age,
                String sex, String phone, String doctor, String date) {
            ContentValues cv = new ContentValues();
            cv.put("remote_id", remoteId);
            cv.put("lab_id", labId);
            cv.put("name", name);
            cv.put("age", age);
            cv.put("sex", sex);
            cv.put("phone", phone);
            cv.put("doctor", doctor);
            cv.put("date", date);

            getWritableDatabase().insert("patients", null, cv);
        }

        Patient patient(long id) {
            Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,remote_id,lab_id,name,age,sex,phone,doctor,date " +
                "FROM patients WHERE id=?",
                new String[]{String.valueOf(id)}
            );

            try {
                if (!c.moveToFirst()) return null;

                Patient p = new Patient();
                p.id = c.getLong(0);
                p.remoteId = c.getString(1);
                p.labId = c.getString(2);
                p.name = c.getString(3);
                p.age = c.getString(4);
                p.sex = c.getString(5);
                p.phone = c.getString(6);
                p.doctor = c.getString(7);
                p.date = c.getString(8);
                return p;
                } finally {
                    c.close();
                }
                }        ArrayList<Patient> allPatients() {
            ArrayList<Patient> list = new ArrayList<>();
            Cursor c = getReadableDatabase().rawQuery(
                "SELECT id FROM patients ORDER BY id DESC", null
            );

            try {
                while (c.moveToNext()) {
                    Patient p = patient(c.getLong(0));
                    if (p != null) list.add(p);
                }
            } finally {
                c.close();
            }

            return list;
        }

        long saveReport(
                String remoteId, String labId, long patientId,
                String test, String results, String created) {

            ContentValues cv = new ContentValues();
            cv.put("remote_id", remoteId);
            cv.put("lab_id", labId);
            cv.put("patient_id", patientId);
            cv.put("test", test);
            cv.put("results", results);
            cv.put("created", created);

            return getWritableDatabase().insert(
                "reports", null, cv
            );
        }

        void markReportPdf(long id, String path) {
            ContentValues cv = new ContentValues();
            cv.put("pdf_path", path);

            getWritableDatabase().update(
                "reports", cv, "id=?",
                new String[]{String.valueOf(id)}
            );
        }        long saveReport(
                String remoteId, String labId, String remotePatientId,
                long patientId, String test, String results, String created) {

            ContentValues cv = new ContentValues();
            cv.put("remote_id", remoteId);
            cv.put("lab_id", labId);
            cv.put("patient_id", patientId);
            cv.put("test", test);
            cv.put("results", results);
            cv.put("created", created);

            return getWritableDatabase().insert(
                "reports", null, cv
            );
        }        ArrayList<Report> allReports() {
            ArrayList<Report> list = new ArrayList<>();

            Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,remote_id,lab_id,patient_id,test,results,created,pdf_path " +
                "FROM reports ORDER BY id DESC", null
            );

            try {
                while (c.moveToNext()) {
                    Report r = new Report();

                    r.id = c.getLong(0);
                    r.remoteId = c.getString(1);
                    r.labId = c.getString(2);
                    r.patientId = c.getLong(3);
                    r.test = c.getString(4);
                    r.results = c.getString(5);
                    r.created = c.getString(6);
                    r.pdfPath = c.getString(7);

                    list.add(r);
                }
            } finally {
                c.close();
            }

            return list;
        }
    }
}
