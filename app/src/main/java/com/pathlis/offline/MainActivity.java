package com.pathlis.offline;

import android.app.*;
import android.os.*;
import android.os.StrictMode;
import org.json.*;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private final int purple = Color.rgb(105,65,180), pale = Color.rgb(248,245,255), dark = Color.rgb(40,35,50);
    private DB db; private SupabaseClient cloud; private LinearLayout root; private EditText[] reportFields; private Patient activePatient; private String activeTest="CBC"; private File pendingPdf;
    private static final int SAVE_PDF=88;
    static class Patient { long id; String remoteId, name, age, sex, phone, doctor, date; }
    static class Param { String name, unit; Double low, high; Param(String n,String u,Double l,Double h){name=n;unit=u;low=l;high=h;} }
    private final Map<String,Param[]> tests = new LinkedHashMap<>();

    @Override public void onCreate(Bundle b){super.onCreate(b); StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build()); db=new DB(this); cloud=new SupabaseClient(); loginScreen();}
    
private void loginScreen() {
    base("Path LIS — Secure Login");

    // Vertically centre the login content
    root.setGravity(Gravity.CENTER_VERTICAL);

    add(text("Online laboratory system • Shared cloud database",
            14, dark, false));

    EditText email = field("Staff email");
    email.setInputType(
            android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
    );

    EditText pass = field("Password");
    pass.setInputType(
            android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
    );

    add(email);
    add(pass);

    add(btn("Login", () -> {
        String em = email.getText().toString().trim();
        String pw = pass.getText().toString();

        if (em.isEmpty() || pw.isEmpty()) {
            toast("Enter email and password");
            return;
        }

        try {
            cloud.login(em, pw);
            getSharedPreferences("auth", 0)
                    .edit()
                    .putString("email", em)
                    .apply();
            syncSharedSettings(false);
            syncLabTestDefinitions(false);
            cloud.logActivity("login", "session", cloud.userId);
            home();
        } catch (Exception ex) {
            toast("Login failed: " + ex.getMessage());
        }
    }));

    add(text(
            "Use the email/password of the Auth user already linked to your lab profile. Internet is required.",
            12, dark, false
    ));
}

private boolean isAdmin() {
    return cloud != null && "admin".equals(cloud.role);
}
    
    private void syncPatientsFromCloud(){try{JSONArray arr=cloud.getPatients();if(arr==null)return;SQLiteDatabase d=db.getWritableDatabase();for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);String rid=o.getString("id");ContentValues cv=new ContentValues();cv.put("remote_id",rid);cv.put("lab_id",o.optString("lab_id"));cv.put("name",o.optString("name"));cv.put("age",o.optString("age"));cv.put("sex",o.optString("sex"));cv.put("phone",o.optString("phone"));cv.put("doctor",o.optString("doctor"));cv.put("date",o.optString("created_at"));Cursor old=d.rawQuery("SELECT id FROM patients WHERE remote_id=?",new String[]{rid});if(old.moveToFirst())d.update("patients",cv,"id=?",new String[]{String.valueOf(old.getLong(0))});else d.insert("patients",null,cv);old.close();}}catch(Exception e){toast("Cloud patient sync failed: "+e.getMessage());}}
    private void syncReportsFromCloud(){try{JSONArray arr=cloud.getReports();if(arr==null)return;SQLiteDatabase d=db.getWritableDatabase();for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);String rid=o.getString("id"),patientRemote=o.getString("patient_id");Cursor pc=d.rawQuery("SELECT id FROM patients WHERE remote_id=?",new String[]{patientRemote});if(!pc.moveToFirst()){pc.close();continue;}long localPatient=pc.getLong(0);pc.close();ContentValues cv=new ContentValues();cv.put("remote_id",rid);cv.put("lab_id",o.optString("lab_id"));cv.put("patient_id",localPatient);cv.put("test",o.optString("test"));Object results=o.opt("results");cv.put("results",results==null?"":results.toString());cv.put("created",o.optString("created_at"));Cursor old=d.rawQuery("SELECT id FROM reports WHERE remote_id=?",new String[]{rid});if(old.moveToFirst())d.update("reports",cv,"id=?",new String[]{String.valueOf(old.getLong(0))});else d.insert("reports",null,cv);old.close();}}catch(Exception e){toast("Cloud report sync failed: "+e.getMessage());}}
private void base(String title) {
    reportFields = null;
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(pale);
    root.setPadding(dp(18), dp(16), dp(18), dp(16));

    ScrollView sc = new ScrollView(this);
    sc.setFillViewport(true);
    sc.setBackgroundColor(pale);
    sc.addView(root, new ScrollView.LayoutParams(-1, -2));

    LinearLayout page = new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setBackgroundColor(pale);

    LinearLayout toolbar = new LinearLayout(this);
    toolbar.setGravity(Gravity.CENTER_VERTICAL);
    Button menu = new Button(this); menu.setText("☰"); menu.setAllCaps(false); menu.setTextColor(purple);
    menu.setContentDescription("Open navigation menu"); menu.setOnClickListener(v -> showNavigationMenu());
    toolbar.addView(menu, new LinearLayout.LayoutParams(dp(52), dp(52)));
    TextView head = text(title, 22, purple, true);
    toolbar.addView(head, new LinearLayout.LayoutParams(0, -2, 1f));
    page.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));

    page.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));

    setContentView(page);
}
    
    @Override public void onBackPressed() {
        if (reportFields != null) {
            boolean hasInput = false;
            for (EditText field : reportFields) if (field != null && !field.getText().toString().trim().isEmpty()) { hasInput = true; break; }
            if (hasInput) {
                new AlertDialog.Builder(this).setTitle("Discard report entry?")
                        .setMessage("Entered results have not been saved as a report. Discard them?")
                        .setNegativeButton("Keep editing", (d,w) -> {})
                        .setPositiveButton("Discard", (d,w) -> { if (activePatient != null) chooseTest(activePatient); else home(); })
                        .show();
            } else if (activePatient != null) chooseTest(activePatient); else home();
            return;
        }
        if (cloud != null && cloud.accessToken != null && !cloud.accessToken.isEmpty()) { home(); return; }
        super.onBackPressed();
    }

    private void add(View v){root.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private void home(){base("Path LIS");ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.pathlis_logo);logo.setContentDescription("Path LIS logo");logo.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(92),dp(92));lp.gravity=Gravity.CENTER_HORIZONTAL;root.addView(logo,lp);add(text("Online Laboratory Information System",15,dark,false));add(text("Hello, "+(cloud.fullName==null||cloud.fullName.trim().isEmpty()?"there":cloud.fullName)+"!",20,dark,true));add(text("Role: "+cloud.role+" • Laboratory: "+pref("lab_name","Lab not configured"),13,dark,false));add(text("Shared settings last updated: "+(pref("config_updated_at","Not synced yet"))+ ("cached".equals(pref("settings_source","")) ? " • CACHED / may be outdated" : ""),11,dark,false));add(text("Shared test configuration: "+("cached".equals(pref("tests_source","")) ? "CACHED / may be outdated" : "server or not yet synced")+" • active tests: "+tests.size(),12,dark,false));add(btn("Refresh Shared Configuration",()->{syncSharedSettings(true);syncLabTestDefinitions(true);home();}));add(btn("Logout",()->{cloud.logActivity("logout","session",cloud.userId);cloud.clearSession();getSharedPreferences("auth",0).edit().clear().apply();loginScreen();}));add(btn("＋  Patient Registration",this::registration));add(btn("Patients & Create Report",()->{syncPatientsFromCloud();patients();}));add(btn("Report History",this::history));if(isAdmin())add(btn("Lab Settings",this::settings));add(text("Use only shared test definitions and reference intervals reviewed and approved by your laboratory before releasing reports.",12,Color.DKGRAY,false));}
    private EditText field(String hint){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setTextSize(16);return e;}
    private void registration(){base("Patient Registration");EditText name=field("Patient full name *"), age=field("Age (e.g. 25 years) *"), phone=field("Mobile number (optional)"), doctor=field("Referring doctor (optional)");Spinner sex=new Spinner(this);sex.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Male","Female","Other"}));add(name);add(age);add(text("Sex",14,dark,true));add(sex);add(phone);add(doctor);add(btn("Save Patient",()->{String n=name.getText().toString().trim(), a=age.getText().toString().trim();if(n.isEmpty()||a.isEmpty()){toast("Enter patient name and age");return;} ContentValues cv=new ContentValues();cv.put("name",n);cv.put("age",a);cv.put("sex",sex.getSelectedItem().toString());cv.put("phone",phone.getText().toString().trim());cv.put("doctor",doctor.getText().toString().trim());cv.put("date",new SimpleDateFormat("dd-MM-yyyy HH:mm",Locale.getDefault()).format(new Date()));try{JSONObject remote=cloud.insertPatient(n,a,sex.getSelectedItem().toString(),phone.getText().toString().trim(),doctor.getText().toString().trim());cv.put("remote_id",remote.getString("id"));cv.put("lab_id",cloud.labId);long id=db.getWritableDatabase().insert("patients",null,cv);if(id>0){cloud.logActivity("patient_created","patient",remote.getString("id"));toast("Patient saved to shared lab database");Patient p=db.patient(id);if(p!=null)chooseTest(p);}else toast("Cloud saved, but local report cache could not be created");}catch(Exception ex){toast("Patient not saved. Check internet / login: "+ex.getMessage());}}));add(btn("Back to Home",this::home));}
    private void patients(){base("Patients");Cursor c=db.getReadableDatabase().rawQuery("SELECT id,name,age,sex,date FROM patients ORDER BY id DESC",null);if(c.getCount()==0)add(text("No patients yet. Register your first patient.",15,dark,false));while(c.moveToNext()){long id=c.getLong(0);String label=c.getString(1)+"  •  "+c.getString(2)+"  •  "+c.getString(3)+"\n"+c.getString(4);add(btn(label,()->{Patient p=db.patient(id);if(p!=null)chooseTest(p);}));}c.close();add(btn("＋ Register Patient",this::registration));add(btn("Back",this::home));}
    private void chooseTest(Patient p){activePatient=p;base("Create Report");add(text(p.name+"  |  Age: "+p.age+"  |  "+p.sex,16,dark,true));add(text("Select a test/profile",14,dark,false));for(String test:tests.keySet())add(btn(test,()->entry(p,test)));add(btn("Back to Patients",this::patients));}
    private void entry(Patient p,String test){activePatient=p;activeTest=test;Param[] ps=tests.get(test);if(ps==null||ps.length==0){toast("No active parameters configured for this test.");return;}base(test+" — Result Entry");add(text("Patient: "+p.name+" | Age: "+p.age+" | "+p.sex,14,dark,true));add(text("Enter measured values. Out-of-range values are highlighted.",13,dark,false));reportFields=new EditText[ps.length];for(int i=0;i<ps.length;i++){LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.VERTICAL);TextView label=text(ps[i].name+" ("+ps[i].unit+")  |  Reference: "+(ps[i].low==null&&ps[i].high==null?"Not configured":(ps[i].low==null?"":ps[i].low)+"–"+(ps[i].high==null?"":ps[i].high)),14,dark,true);EditText value=field("Result value");reportFields[i]=value;line.addView(label);line.addView(value);root.addView(line);}add(btn("Generate PDF Report",()->{ArrayList<String> vals=new ArrayList<>();boolean any=false;for(EditText e:reportFields){String v=e.getText().toString().trim();vals.add(v);if(!v.isEmpty())any=true;}if(!any){toast("Enter at least one result value");return;}try{if(p.remoteId==null||p.remoteId.isEmpty())throw new Exception("Patient is not linked to the cloud database. Re-open Patients and select the patient again.");JSONObject cloudResults=new JSONObject();for(int j=0;j<ps.length;j++){JSONObject item=new JSONObject();item.put("value",vals.get(j));item.put("unit",ps[j].unit);if(ps[j].low!=null)item.put("reference_low",ps[j].low);if(ps[j].high!=null)item.put("reference_high",ps[j].high);cloudResults.put(ps[j].name,item);}JSONObject savedReport=cloud.insertReport(p.remoteId,test,cloudResults);cloud.logActivity("report_created","report",savedReport.optString("id"));long rid=db.saveReport(p.id,test,ps,vals);pendingPdf=createPdf(p,test,ps,vals,rid);showPdfActions();}catch(Exception ex){toast("Report not saved to shared database: "+ex.getMessage());}}));add(btn("Back to Test Selection",()->chooseTest(p)));}
    private void showPdfActions(){new AlertDialog.Builder(this).setTitle("Report generated").setMessage("PDF created. Choose what to do next.").setPositiveButton("Save to Downloads",(d,w)->savePdf()).setNeutralButton("Share PDF",(d,w)->sharePdf()).setNegativeButton("Later",(d,w)->home()).show();}
    private File createPdf(Patient p,String test,Param[] ps,ArrayList<String> vals,long reportId)throws Exception{File dir=new File(getFilesDir(),"reports");if(!dir.exists())dir.mkdirs();File file=new File(dir,"PathLIS_Report_"+reportId+".pdf");android.graphics.pdf.PdfDocument doc=new android.graphics.pdf.PdfDocument();android.graphics.pdf.PdfDocument.Page page=doc.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,1).create());Canvas c=page.getCanvas();Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.BLACK);paint.setTextSize(18);String lab=pref("lab_name","Maa PathLab");c.drawText(lab,40,50,paint);paint.setTextSize(10);c.drawText(pref("lab_address","Address not configured"),40,70,paint);c.drawLine(40,84,555,84,paint);paint.setTextSize(15);c.drawText("LABORATORY REPORT",40,112,paint);paint.setTextSize(11);c.drawText("Patient: "+p.name,40,140,paint);c.drawText("Age/Sex: "+p.age+" / "+p.sex,40,160,paint);c.drawText("Mobile: "+(p.phone.isEmpty()?"—":p.phone),40,180,paint);c.drawText("Ref. Doctor: "+(p.doctor.isEmpty()?"—":p.doctor),300,160,paint);c.drawText("Date: "+new SimpleDateFormat("dd-MM-yyyy HH:mm",Locale.getDefault()).format(new Date()),300,180,paint);paint.setTextSize(13);paint.setTypeface(Typeface.DEFAULT_BOLD);c.drawText(test,40,220,paint);paint.setTypeface(Typeface.DEFAULT);c.drawText("Parameter",40,250,paint);c.drawText("Result",270,250,paint);c.drawText("Unit",360,250,paint);c.drawText("Reference range",440,250,paint);c.drawLine(40,258,555,258,paint);int y=282;int printed=0;for(int i=0;i<ps.length;i++){String v=vals.get(i).trim();if(v.isEmpty())continue; // Do not print parameters for which no reading was entered.
            c.drawText(ps[i].name,40,y,paint);c.drawText(v,270,y,paint);c.drawText(ps[i].unit,360,y,paint);c.drawText((ps[i].low==null&&ps[i].high==null?"Not configured":(ps[i].low==null?"":ps[i].low)+"–"+(ps[i].high==null?"":ps[i].high)),440,y,paint);try{double n=Double.parseDouble(v);if((ps[i].low!=null&&n<ps[i].low)||(ps[i].high!=null&&n>ps[i].high)){paint.setColor(Color.RED);c.drawText("H/L",520,y,paint);paint.setColor(Color.BLACK);}}catch(Exception ignored){}y+=24;printed++;}paint.setTextSize(9);int footerY=Math.min(760,Math.max(710,y+28));c.drawText("Reference ranges are general guides; verify method-specific ranges and review results before release.",40,footerY,paint);paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(12);c.drawText("LT",510,footerY+42,paint);paint.setTypeface(Typeface.DEFAULT);doc.finishPage(page);FileOutputStream out=new FileOutputStream(file);doc.writeTo(out);out.close();doc.close();db.markReportPdf(reportId,file.getAbsolutePath());return file;}
    private void savePdf(){if(pendingPdf==null){toast("Generate a report first");return;}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/pdf");i.putExtra(Intent.EXTRA_TITLE,pendingPdf.getName());startActivityForResult(i,SAVE_PDF);}
    private void sharePdf(){if(pendingPdf==null){toast("Generate a report first");return;}try{Uri uri=FileProvider.getUriForFile(this,"com.pathlis.offline.fileprovider",pendingPdf);Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/pdf");i.putExtra(Intent.EXTRA_STREAM,uri);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Share laboratory report"));}catch(Exception e){toast("Sharing error: "+e.getMessage());}}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==SAVE_PDF&&res==RESULT_OK&&data!=null&&data.getData()!=null){try(InputStream in=new FileInputStream(pendingPdf);OutputStream out=getContentResolver().openOutputStream(data.getData())){byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);toast("PDF saved successfully");}catch(Exception e){toast("Could not save PDF: "+e.getMessage());}}}
    private void history(){syncPatientsFromCloud();syncReportsFromCloud();base("Report History");Cursor c=db.getReadableDatabase().rawQuery("SELECT r.id,p.name,r.test,r.created FROM reports r JOIN patients p ON p.id=r.patient_id ORDER BY r.id DESC",null);if(c.getCount()==0)add(text("No reports generated yet.",15,dark,false));while(c.moveToNext()){long id=c.getLong(0);String title=c.getString(1)+" — "+c.getString(2)+"\n"+c.getString(3);add(btn(title,()->{String path=db.reportPath(id);if(path==null||!new File(path).exists()){toast("PDF file unavailable. Generate the report again.");return;}pendingPdf=new File(path);new AlertDialog.Builder(this).setTitle("Report").setItems(new String[]{"Save PDF to Downloads","Share PDF"},(d,w)->{if(w==0)savePdf();else sharePdf();}).show();}));}c.close();add(btn("Back",this::home));}
    private void settings(){
        if(!isAdmin()){toast("Only Admin can change lab settings");return;}
        base("Laboratory Settings");
        add(text("Shared settings apply to this lab after saving to Supabase.",13,dark,false));
        EditText lab=field("Laboratory name");lab.setText(pref("lab_name","Maa PathLab"));
        EditText addr=field("Laboratory address");addr.setText(pref("lab_address",""));
        EditText tech=field("Technologist name");tech.setText(pref("tech_name",""));
        EditText td=field("Technologist designation");td.setText(pref("tech_designation","Lab Technician"));
        EditText path=field("Pathologist name (optional)");path.setText(pref("path_name",""));
        EditText pd=field("Pathologist designation");pd.setText(pref("path_designation","Pathologist"));
        add(lab);add(addr);add(tech);add(td);add(path);add(pd);
        add(btn("Save Shared Settings",()->{
            try {
                JSONObject values=new JSONObject(); values.put("lab_name",lab.getText().toString().trim());
                values.put("lab_address",addr.getText().toString().trim()); values.put("tech_name",tech.getText().toString().trim());
                values.put("tech_designation",td.getText().toString().trim()); values.put("path_name",path.getText().toString().trim());
                values.put("path_designation",pd.getText().toString().trim());
                JSONObject saved=cloud.saveLabSettings(values); applySharedSettings(saved);
                cloud.logActivity("lab_settings_updated","lab_settings",cloud.labId);
                toast("Shared settings saved to Supabase");home();
            } catch(Exception ex){toast("Shared save failed; local settings were not changed: "+ex.getMessage());}
        }));
        add(btn("Refresh Shared Settings",()->{syncSharedSettings(true);settings();}));
    }
    private void applySharedSettings(JSONObject o){
        if(o==null)return; android.content.SharedPreferences.Editor e=getSharedPreferences("lab",0).edit();
        String[] keys={"lab_name","lab_address","tech_name","tech_designation","path_name","path_designation"};
        for(String k:keys)if(o.has(k)&&!o.isNull(k))e.putString(k,o.optString(k));
        e.putString("config_updated_at",o.optString("updated_at",""));e.putLong("config_version",o.optLong("config_version",0));e.putString("settings_source","server");e.apply();
    }
    private void syncSharedSettings(boolean showMessage){
        try{JSONObject o=cloud.getLabSettings();if(o==null){if(showMessage)toast("No shared settings row found. Ask an admin to save settings first.");return;}applySharedSettings(o);if(showMessage)toast("Shared configuration refreshed: "+o.optString("updated_at","timestamp unavailable"));}
        catch(Exception ex){String cached=pref("config_updated_at","");if(!cached.isEmpty())getSharedPreferences("lab",0).edit().putString("settings_source","cached").apply();if(showMessage)toast(cached.isEmpty()?"Configuration refresh failed; no cached shared settings available: "+ex.getMessage():"Offline: using cached shared settings from "+cached+". Refresh failed: "+ex.getMessage());}
    }
    private void syncLabTestDefinitions(boolean showMessage) {
        try {
            JSONArray remote = cloud.getLabTests(false);
            applyTestDefinitions(remote);
            String timestamp = latestServerConfigTimestamp(remote);
            getSharedPreferences("lab", 0).edit().putString("tests_updated_at", timestamp)
                    .putString("tests_cache_json", remote.toString()).putString("tests_source", "server").apply();
            if (showMessage) toast("Shared tests refreshed: " + tests.size() + " active test(s).");
        } catch (Exception ex) {
            String cached = pref("tests_cache_json", "");
            if (!cached.isEmpty()) {
                try {
                    applyTestDefinitions(new JSONArray(cached));
                    getSharedPreferences("lab", 0).edit().putString("tests_source", "cached").apply();
                    if (showMessage) toast("Offline: using cached test configuration from " + pref("tests_updated_at", "unknown") + ". It may be outdated.");
                } catch (Exception cacheError) {
                    if (showMessage) toast("Shared test refresh failed and cached configuration could not be read: " + ex.getMessage());
                }
            } else if (showMessage) toast("Shared test refresh failed; no cached tests are available. Configure tests as Admin. " + ex.getMessage());
        }
    }

    private String latestServerConfigTimestamp(JSONArray remote) throws Exception {
        String latest = "";
        for (int i = 0; i < remote.length(); i++) {
            JSONObject test = remote.getJSONObject(i);
            String t = test.optString("updated_at", ""); if (t.compareTo(latest) > 0) latest = t;
            JSONArray parameters = test.optJSONArray("parameters");
            if (parameters != null) for (int j = 0; j < parameters.length(); j++) {
                String p = parameters.getJSONObject(j).optString("updated_at", "");
                if (p.compareTo(latest) > 0) latest = p;
            }
        }
        return latest.isEmpty() ? "Server timestamp unavailable" : latest;
    }

    private void applyTestDefinitions(JSONArray remote) throws Exception {
        LinkedHashMap<String, Param[]> loaded = new LinkedHashMap<>();
        for (int i = 0; i < remote.length(); i++) {
            JSONObject t = remote.getJSONObject(i); JSONArray a = t.optJSONArray("parameters");
            ArrayList<Param> ps = new ArrayList<>();
            if (a != null) for (int j = 0; j < a.length(); j++) {
                JSONObject p = a.getJSONObject(j);
                Double lo = p.isNull("reference_low") ? null : p.optDouble("reference_low");
                Double hi = p.isNull("reference_high") ? null : p.optDouble("reference_high");
                ps.add(new Param(p.optString("parameter_name"), p.optString("unit"), lo, hi));
            }
            if (!ps.isEmpty()) loaded.put(t.optString("test_name"), ps.toArray(new Param[0]));
        }
        tests.clear(); tests.putAll(loaded);
    }

    private void testManagement() {
        if (!isAdmin()) { toast("Only Admin can manage tests"); return; }
        base("Test & Parameter Management");
        add(text("Configure and validate these test definitions before clinical use.", 13, dark, false));
        add(btn("＋ Add Test", () -> {
            EditText name = field("Test/profile name *"), order = field("Display order (optional)"); order.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.addView(name); form.addView(order);
            new AlertDialog.Builder(this).setTitle("Add shared test").setView(form).setNegativeButton("Cancel", null).setPositiveButton("Save", (d,w) -> {
                String n = name.getText().toString().trim(); if (n.isEmpty()) { toast("Enter a test name"); return; }
                try { cloud.createLabTest(n, parseIntOrZero(order.getText().toString())); cloud.logActivity("test_created", "lab_test", n); syncLabTestDefinitions(false); testManagement(); }
                catch (Exception ex) { toast("Test was not saved: " + ex.getMessage()); }
            }).show();
        }));
        try {
            JSONArray all = cloud.getLabTests(true);
            if (all.length() == 0) add(text("No shared tests configured. Add a test and its parameters.", 14, dark, false));
            for (int i=0; i<all.length(); i++) {
                JSONObject t=all.getJSONObject(i); String id=t.optString("id"), name=t.optString("test_name"); boolean active=t.optBoolean("is_active",true);
                add(text(name + (active ? " • Active" : " • Inactive"), 16, dark, true));
                add(btn("Edit test", () -> editTest(id, name, t.optInt("sort_order", 0))));
                add(btn(active ? "Deactivate test" : "Activate test", () -> changeActive("lab_tests", id, !active)));
                add(btn("＋ Add parameter to " + name, () -> addParameter(id,name)));
                JSONArray ps=t.optJSONArray("parameters");
                if(ps!=null) for(int j=0;j<ps.length();j++) { JSONObject p=ps.getJSONObject(j); String pid=p.optString("id"), pn=p.optString("parameter_name"); boolean pa=p.optBoolean("is_active",true);
                    add(text("• " + pn + " (" + p.optString("unit") + ") • ref " + (p.isNull("reference_low")?"":p.optString("reference_low")) + "–" + (p.isNull("reference_high")?"":p.optString("reference_high")) + (pa?"":" [Inactive]"),13,dark,false));
                    add(btn("Edit " + pn, () -> editParameter(pid, pn, p.optString("unit"), p.isNull("reference_low")?"":p.optString("reference_low"), p.isNull("reference_high")?"":p.optString("reference_high"), p.optInt("sort_order",0))));
                    add(btn((pa?"Deactivate ":"Activate ")+pn, () -> changeActive("lab_test_parameters",pid,!pa)));
                }
            }
        } catch(Exception ex) { add(text("Could not load shared configuration: "+ex.getMessage(),13,Color.RED,false)); }
        add(btn("Refresh tests", () -> { syncLabTestDefinitions(true); testManagement(); })); add(btn("Back to Home",this::home));
    }

    private void editTest(String id, String oldName, int oldOrder) {
        if (!isAdmin()) { toast("Admin access required"); return; }
        EditText name=field("Test/profile name *"), order=field("Display order"); name.setText(oldName); order.setText(String.valueOf(oldOrder)); order.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.addView(name); form.addView(order);
        new AlertDialog.Builder(this).setTitle("Edit shared test").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
            String n=name.getText().toString().trim(); if(n.isEmpty()){toast("Enter a test name");return;}
            try{cloud.updateLabTest(id,n,parseIntOrZero(order.getText().toString()));cloud.logActivity("test_updated","lab_test",id);syncLabTestDefinitions(false);testManagement();}
            catch(Exception ex){toast("Test update failed: "+ex.getMessage());}
        }).show();
    }

    private void editParameter(String id, String oldName, String oldUnit, String oldLow, String oldHigh, int oldOrder) {
        if (!isAdmin()) { toast("Admin access required"); return; }
        EditText name=field("Parameter name *"),unit=field("Unit"),low=field("Reference lower limit (optional)"),high=field("Reference upper limit (optional)"),order=field("Display order");
        name.setText(oldName);unit.setText(oldUnit);low.setText(oldLow);high.setText(oldHigh);order.setText(String.valueOf(oldOrder));
        int numeric=android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED;
        low.setInputType(numeric);high.setInputType(numeric);order.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.addView(name);form.addView(unit);form.addView(low);form.addView(high);form.addView(order);
        new AlertDialog.Builder(this).setTitle("Edit shared parameter").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
            String n=name.getText().toString().trim();if(n.isEmpty()){toast("Enter parameter name");return;}
            try{Double l=parseNullableDouble(low.getText().toString()),h=parseNullableDouble(high.getText().toString());if(l!=null&&h!=null&&l>h)throw new Exception("Lower reference must not exceed upper reference.");cloud.updateLabParameter(id,n,unit.getText().toString(),l,h,parseIntOrZero(order.getText().toString()));cloud.logActivity("parameter_updated","lab_parameter",id);syncLabTestDefinitions(false);testManagement();}
            catch(Exception ex){toast("Parameter update failed: "+ex.getMessage());}
        }).show();
    }

    private void addParameter(String testId, String testName) {
        EditText name=field("Parameter name *"), unit=field("Unit"), low=field("Reference lower limit (optional)"), high=field("Reference upper limit (optional)"), order=field("Display order (optional)");
        int numeric=android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED;
        low.setInputType(numeric); high.setInputType(numeric); order.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.addView(name);form.addView(unit);form.addView(low);form.addView(high);form.addView(order);
        new AlertDialog.Builder(this).setTitle("Add parameter — "+testName).setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
            String n=name.getText().toString().trim(); if(n.isEmpty()){toast("Enter parameter name");return;}
            try { Double l=parseNullableDouble(low.getText().toString()), h=parseNullableDouble(high.getText().toString()); if(l!=null&&h!=null&&l>h)throw new Exception("Lower reference must not exceed upper reference."); cloud.createLabParameter(testId,n,unit.getText().toString(),l,h,parseIntOrZero(order.getText().toString())); cloud.logActivity("parameter_created","lab_test",testName+":"+n);syncLabTestDefinitions(false);testManagement(); }
            catch(Exception ex){toast("Parameter was not saved: "+ex.getMessage());}
        }).show();
    }
    private void changeActive(String table,String id,boolean active){if(!isAdmin()){toast("Admin access required");return;}try{cloud.setConfigurationActive(table,id,active);syncLabTestDefinitions(false);testManagement();}catch(Exception ex){toast("Configuration was not changed: "+ex.getMessage());}}
    private int parseIntOrZero(String v){try{return Integer.parseInt(v.trim());}catch(Exception ignored){return 0;}}
    private Double parseNullableDouble(String v)throws Exception{String x=v.trim();if(x.isEmpty())return null;try{return Double.valueOf(x);}catch(NumberFormatException ex){throw new Exception("Reference limits must be numeric.");}}

    private void showNavigationMenu(){
        if(cloud==null||cloud.accessToken.isEmpty()){loginScreen();return;}
        ArrayList<String> labels=new ArrayList<>(Arrays.asList("Home / Dashboard","Patient Registration","Patients","Create Report","Report History","Refresh Shared Configuration","Account / Profile"));
        if(isAdmin()){labels.add("Laboratory Settings — Admin");labels.add("Test & Parameter Management — Admin");labels.add("Staff Activity — Admin");}
        labels.add("Logout");
        new AlertDialog.Builder(this).setTitle("Path LIS Navigation").setItems(labels.toArray(new String[0]),(d,which)->{
            String item=labels.get(which);
            if(item.startsWith("Home"))home(); else if(item.equals("Patient Registration"))registration();
            else if(item.equals("Patients")){syncPatientsFromCloud();patients();}
            else if(item.equals("Create Report")){syncPatientsFromCloud();patients();}
            else if(item.equals("Report History"))history();
            else if(item.startsWith("Refresh")){syncSharedSettings(true);syncLabTestDefinitions(true);home();}
            else if(item.startsWith("Account")){base("Account / Profile");add(text("Name: "+cloud.fullName,16,dark,true));add(text("Role: "+cloud.role,14,dark,false));add(text("Laboratory ID: "+cloud.labId,12,dark,false));add(btn("Back to Home",this::home));}
            else if(item.startsWith("Laboratory Settings")){if(isAdmin())settings();else toast("Admin access required");}
            else if(item.startsWith("Test & Parameter Management")){if(isAdmin())testManagement();else toast("Admin access required");}
            else if(item.startsWith("Staff Activity")){if(isAdmin())staffActivity();else toast("Admin access required");}
            else if(item.equals("Logout")){cloud.logActivity("logout","session",cloud.userId);cloud.clearSession();getSharedPreferences("auth",0).edit().clear().apply();loginScreen();}
        }).show();
    }
    private void staffActivity(){if(!isAdmin()){toast("Admin access required");return;}base("Staff Activity");add(text("Activity log for this laboratory",14,dark,false));try{JSONArray rows=cloud.getActivity();if(rows.length()==0)add(text("No activity recorded yet.",14,dark,false));for(int i=0;i<rows.length();i++){JSONObject o=rows.getJSONObject(i);add(text(o.optString("created_at")+" • "+o.optString("action")+" • actor "+o.optString("actor_id"),13,dark,false));}}catch(Exception ex){add(text("Could not load activity: "+ex.getMessage(),13,Color.RED,false));}add(btn("Back to Home",this::home));}
    private String pref(String k,String d){return getSharedPreferences("lab",0).getString(k,d);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}

    private class DB extends SQLiteOpenHelper {
        DB(Context c){super(c,"pathlis.db",null,2);}
        public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE patients(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,age TEXT NOT NULL,sex TEXT,phone TEXT,doctor TEXT,date TEXT,remote_id TEXT,lab_id TEXT)");d.execSQL("CREATE TABLE reports(id INTEGER PRIMARY KEY AUTOINCREMENT,patient_id INTEGER,test TEXT,results TEXT,created TEXT,pdf_path TEXT,remote_id TEXT,lab_id TEXT)");}
        public void onUpgrade(SQLiteDatabase d,int o,int n){if(o<2){try{d.execSQL("ALTER TABLE patients ADD COLUMN remote_id TEXT");}catch(Exception ignored){}try{d.execSQL("ALTER TABLE patients ADD COLUMN lab_id TEXT");}catch(Exception ignored){}try{d.execSQL("ALTER TABLE reports ADD COLUMN remote_id TEXT");}catch(Exception ignored){}try{d.execSQL("ALTER TABLE reports ADD COLUMN lab_id TEXT");}catch(Exception ignored){}}}
        Patient patient(long id){Cursor c=getReadableDatabase().rawQuery("SELECT id,remote_id,name,age,sex,phone,doctor,date FROM patients WHERE id=?",new String[]{String.valueOf(id)});Patient p=null;if(c.moveToFirst()){p=new Patient();p.id=c.getLong(0);p.remoteId=c.getString(1);p.name=c.getString(2);p.age=c.getString(3);p.sex=c.getString(4);p.phone=c.getString(5)==null?"":c.getString(5);p.doctor=c.getString(6)==null?"":c.getString(6);p.date=c.getString(7);}c.close();return p;}
        long saveReport(long pid,String test,Param[] ps,ArrayList<String> vals){StringBuilder b=new StringBuilder();for(int i=0;i<ps.length;i++){if(i>0)b.append("|");b.append(ps[i].name.replace("|"," ")).append("=").append(vals.get(i).replace("|"," "));}ContentValues cv=new ContentValues();cv.put("patient_id",pid);cv.put("test",test);cv.put("results",b.toString());cv.put("created",new SimpleDateFormat("dd-MM-yyyy HH:mm",Locale.getDefault()).format(new Date()));return getWritableDatabase().insert("reports",null,cv);}
        void markReportPdf(long id,String path){ContentValues cv=new ContentValues();cv.put("pdf_path",path);getWritableDatabase().update("reports",cv,"id=?",new String[]{String.valueOf(id)});}
        String reportPath(long id){Cursor c=getReadableDatabase().rawQuery("SELECT pdf_path FROM reports WHERE id=?",new String[]{String.valueOf(id)});String p=null;if(c.moveToFirst())p=c.getString(0);c.close();return p;}
    }
        }
