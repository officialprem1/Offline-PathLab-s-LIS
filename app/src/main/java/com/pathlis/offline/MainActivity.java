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
    static class Param { String name, unit; double low, high; Param(String n,String u,double l,double h){name=n;unit=u;low=l;high=h;} }
    private final Map<String,Param[]> tests = new LinkedHashMap<>();

    @Override public void onCreate(Bundle b){super.onCreate(b); StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build()); db=new DB(this); cloud=new SupabaseClient(); initTests(); loginScreen();}
    private void loginScreen(){base("Path LIS — Secure Login");add(text("Online laboratory system • Shared cloud database",14,dark,false));EditText email=field("Staff email");email.setInputType( android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);EditText pass=field("Password");pass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);add(email);add(pass);add(btn("Login",()->{String em=email.getText().toString().trim(),pw=pass.getText().toString();if(em.isEmpty()||pw.isEmpty()){toast("Enter email and password");return;}try{cloud.login(em,pw);getSharedPreferences("auth",0).edit().putString("email",em).apply();home();}catch(Exception ex){toast("Login failed: "+ex.getMessage());}}));add(text("Use the email/password of the Auth user already linked to your lab profile. Internet is required.",12,dark,false));}
    private boolean isAdmin(){return cloud!=null && "admin".equals(cloud.role);}
    private void syncPatientsFromCloud(){try{JSONArray arr=cloud.getPatients();if(arr==null)return;SQLiteDatabase d=db.getWritableDatabase();for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);String rid=o.getString("id");ContentValues cv=new ContentValues();cv.put("remote_id",rid);cv.put("lab_id",o.optString("lab_id"));cv.put("name",o.optString("name"));cv.put("age",o.optString("age"));cv.put("sex",o.optString("sex"));cv.put("phone",o.optString("phone"));cv.put("doctor",o.optString("doctor"));cv.put("date",o.optString("created_at"));Cursor old=d.rawQuery("SELECT id FROM patients WHERE remote_id=?",new String[]{rid});if(old.moveToFirst())d.update("patients",cv,"id=?",new String[]{String.valueOf(old.getLong(0))});else d.insert("patients",null,cv);old.close();}}catch(Exception e){toast("Cloud patient sync failed: "+e.getMessage());}}
    private void syncReportsFromCloud(){try{JSONArray arr=cloud.getReports();if(arr==null)return;SQLiteDatabase d=db.getWritableDatabase();for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);String rid=o.getString("id"),patientRemote=o.getString("patient_id");Cursor pc=d.rawQuery("SELECT id FROM patients WHERE remote_id=?",new String[]{patientRemote});if(!pc.moveToFirst()){pc.close();continue;}long localPatient=pc.getLong(0);pc.close();ContentValues cv=new ContentValues();cv.put("remote_id",rid);cv.put("lab_id",o.optString("lab_id"));cv.put("patient_id",localPatient);cv.put("test",o.optString("test"));Object results=o.opt("results");cv.put("results",results==null?"":results.toString());cv.put("created",o.optString("created_at"));Cursor old=d.rawQuery("SELECT id FROM reports WHERE remote_id=?",new String[]{rid});if(old.moveToFirst())d.update("reports",cv,"id=?",new String[]{String.valueOf(old.getLong(0))});else d.insert("reports",null,cv);old.close();}}catch(Exception e){toast("Cloud report sync failed: "+e.getMessage());}}
    private void initTests(){
        // Common adult reference intervals only. Verify against the laboratory method,
        // analyzer insert, patient age/sex and local approved ranges before clinical use.
        tests.put("CBC",new Param[]{
            new Param("Hemoglobin","g/dL",12.0,17.5),
            new Param("Total WBC Count","/µL",4000,11000),
            new Param("RBC Count","million/µL",4.0,6.0),
            new Param("Hematocrit / PCV","%",36,52),
            new Param("MCV","fL",80,100),
            new Param("MCH","pg",27,33),
            new Param("MCHC","g/dL",32,36),
            new Param("RDW-CV","%",11.5,14.5),
            new Param("Platelet Count","/µL",150000,450000),
            new Param("MPV","fL",7.5,12.0),
            new Param("Neutrophils","%",40,80),
            new Param("Lymphocytes","%",20,40),
            new Param("Monocytes","%",2,10),
            new Param("Eosinophils","%",1,6),
            new Param("Basophils","%",0,2),
            new Param("ESR (1st hour)","mm/hr",0,20)
        });
        tests.put("LFT",new Param[]{
            new Param("Total Bilirubin","mg/dL",0.2,1.2),
            new Param("Direct Bilirubin","mg/dL",0.0,0.3),
            new Param("Indirect Bilirubin","mg/dL",0.1,0.9),
            new Param("AST / SGOT","U/L",5,40),
            new Param("ALT / SGPT","U/L",7,56),
            new Param("Alkaline Phosphatase","U/L",44,147),
            new Param("GGT","U/L",9,48),
            new Param("Total Protein","g/dL",6.0,8.3),
            new Param("Albumin","g/dL",3.5,5.0),
            new Param("Globulin","g/dL",2.0,3.5),
            new Param("A/G Ratio","ratio",1.0,2.2)
        });
        tests.put("KFT",new Param[]{
            new Param("Blood Urea","mg/dL",15,45),
            new Param("BUN","mg/dL",7,20),
            new Param("Creatinine","mg/dL",0.6,1.3),
            new Param("Uric Acid","mg/dL",3.5,7.2),
            new Param("Sodium","mmol/L",135,145),
            new Param("Potassium","mmol/L",3.5,5.1),
            new Param("Chloride","mmol/L",98,107),
            new Param("Calcium, Total","mg/dL",8.5,10.5),
            new Param("Phosphorus","mg/dL",2.5,4.5),
            new Param("eGFR","mL/min/1.73m²",60,140)
        });
        tests.put("Lipid Profile",new Param[]{
            new Param("Total Cholesterol","mg/dL",0,200),
            new Param("Triglycerides","mg/dL",0,150),
            new Param("HDL Cholesterol","mg/dL",40,1000),
            new Param("LDL Cholesterol","mg/dL",0,100),
            new Param("VLDL Cholesterol","mg/dL",5,40),
            new Param("Non-HDL Cholesterol","mg/dL",0,130),
            new Param("Total Cholesterol / HDL Ratio","ratio",0,5.0)
        });
        tests.put("Blood Sugar",new Param[]{
            new Param("Fasting Plasma Glucose","mg/dL",70,99),
            new Param("2-hour Post Meal Glucose","mg/dL",70,140),
            new Param("Random Plasma Glucose","mg/dL",70,200),
            new Param("HbA1c","%",4.0,5.6)
        });
        tests.put("Thyroid Profile",new Param[]{
            new Param("Total T3","ng/mL",0.8,2.0),
            new Param("Total T4","µg/dL",5.0,12.0),
            new Param("TSH","µIU/mL",0.4,4.5)
        });
        tests.put("Iron Profile",new Param[]{
            new Param("Serum Iron","µg/dL",60,170),
            new Param("TIBC","µg/dL",250,450),
            new Param("Transferrin Saturation","%",20,50),
            new Param("Ferritin","ng/mL",15,300)
        });
        tests.put("Electrolytes",new Param[]{
            new Param("Sodium","mmol/L",135,145),
            new Param("Potassium","mmol/L",3.5,5.1),
            new Param("Chloride","mmol/L",98,107),
            new Param("Bicarbonate","mmol/L",22,29)
        });
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,1);t.setPadding(dp(4),dp(6),dp(4),dp(6));return t;}
    private Button btn(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(v->r.run());return b;}
    private void base(String title){root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(pale);root.setPadding(dp(18),dp(16),dp(18),dp(16)); ScrollView sc=new ScrollView(this);sc.addView(root); LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL); TextView head=text(title,24,purple,true);page.addView(head);page.addView(sc);setContentView(page);}
    private void add(View v){root.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private void home(){base("Path LIS");ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.pathlis_logo);logo.setContentDescription("Path LIS logo");logo.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(92),dp(92));lp.gravity=Gravity.CENTER_HORIZONTAL;root.addView(logo,lp);add(text("Online Laboratory Information System",15,dark,false));add(text("Signed in: "+cloud.fullName+" • Role: "+cloud.role,13,dark,false));add(btn("Logout",()->{cloud=new SupabaseClient();loginScreen();}));add(btn("＋  Patient Registration",this::registration));add(btn("Patients & Create Report",()->{syncPatientsFromCloud();patients();}));add(btn("Report History",this::history));if(isAdmin())add(btn("Lab Settings",this::settings));add(text("Reference ranges are common adult guides only. Confirm your lab's validated ranges before issuing reports.",12,Color.DKGRAY,false));}
    private EditText field(String hint){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setTextSize(16);return e;}
    private void registration(){base("Patient Registration");EditText name=field("Patient full name *"), age=field("Age (e.g. 25 years) *"), phone=field("Mobile number (optional)"), doctor=field("Referring doctor (optional)");Spinner sex=new Spinner(this);sex.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Male","Female","Other"}));add(name);add(age);add(text("Sex",14,dark,true));add(sex);add(phone);add(doctor);add(btn("Save Patient",()->{String n=name.getText().toString().trim(), a=age.getText().toString().trim();if(n.isEmpty()||a.isEmpty()){toast("Enter patient name and age");return;} ContentValues cv=new ContentValues();cv.put("name",n);cv.put("age",a);cv.put("sex",sex.getSelectedItem().toString());cv.put("phone",phone.getText().toString().trim());cv.put("doctor",doctor.getText().toString().trim());cv.put("date",new SimpleDateFormat("dd-MM-yyyy HH:mm",Locale.getDefault()).format(new Date()));try{JSONObject remote=cloud.insertPatient(n,a,sex.getSelectedItem().toString(),phone.getText().toString().trim(),doctor.getText().toString().trim());cv.put("remote_id",remote.getString("id"));cv.put("lab_id",cloud.labId);long id=db.getWritableDatabase().insert("patients",null,cv);if(id>0){toast("Patient saved to shared lab database");Patient p=db.patient(id);if(p!=null)chooseTest(p);}else toast("Cloud saved, but local report cache could not be created");}catch(Exception ex){toast("Patient not saved. Check internet / login: "+ex.getMessage());}}));add(btn("Back to Home",this::home));}
    private void patients(){base("Patients");Cursor c=db.getReadableDatabase().rawQuery("SELECT id,name,age,sex,date FROM patients ORDER BY id DESC",null);if(c.getCount()==0)add(text("No patients yet. Register your first patient.",15,dark,false));while(c.moveToNext()){long id=c.getLong(0);String label=c.getString(1)+"  •  "+c.getString(2)+"  •  "+c.getString(3)+"\n"+c.getString(4);add(btn(label,()->{Patient p=db.patient(id);if(p!=null)chooseTest(p);}));}c.close();add(btn("＋ Register Patient",this::registration));add(btn("Back",this::home));}
    private void chooseTest(Patient p){activePatient=p;base("Create Report");add(text(p.name+"  |  Age: "+p.age+"  |  "+p.sex,16,dark,true));add(text("Select a test/profile",14,dark,false));for(String test:tests.keySet())add(btn(test,()->entry(p,test)));add(btn("Back to Patients",this::patients));}
    private void entry(Patient p,String test){activePatient=p;activeTest=test;Param[] ps=tests.get(test);base(test+" — Result Entry");add(text("Patient: "+p.name+" | Age: "+p.age+" | "+p.sex,14,dark,true));add(text("Enter measured values. Out-of-range values are highlighted.",13,dark,false));reportFields=new EditText[ps.length];for(int i=0;i<ps.length;i++){LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.VERTICAL);TextView label=text(ps[i].name+" ("+ps[i].unit+")  |  Reference: "+ps[i].low+"–"+ps[i].high,14,dark,true);EditText value=field("Result value");reportFields[i]=value;line.addView(label);line.addView(value);root.addView(line);}add(btn("Generate PDF Report",()->{ArrayList<String> vals=new ArrayList<>();boolean any=false;for(EditText e:reportFields){String v=e.getText().toString().trim();vals.add(v);if(!v.isEmpty())any=true;}if(!any){toast("Enter at least one result value");return;}try{if(p.remoteId==null||p.remoteId.isEmpty())throw new Exception("Patient is not linked to the cloud database. Re-open Patients and select the patient again.");JSONObject cloudResults=new JSONObject();for(int j=0;j<ps.length;j++){JSONObject item=new JSONObject();item.put("value",vals.get(j));item.put("unit",ps[j].unit);item.put("reference_low",ps[j].low);item.put("reference_high",ps[j].high);cloudResults.put(ps[j].name,item);}cloud.insertReport(p.remoteId,test,cloudResults);long rid=db.saveReport(p.id,test,ps,vals);pendingPdf=createPdf(p,test,ps,vals,rid);showPdfActions();}catch(Exception ex){toast("Report not saved to shared database: "+ex.getMessage());}}));add(btn("Back to Test Selection",()->chooseTest(p)));}
    private void showPdfActions(){new AlertDialog.Builder(this).setTitle("Report generated").setMessage("PDF created. Choose what to do next.").setPositiveButton("Save to Downloads",(d,w)->savePdf()).setNeutralButton("Share PDF",(d,w)->sharePdf()).setNegativeButton("Later",(d,w)->home()).show();}
    private File createPdf(Patient p,String test,Param[] ps,ArrayList<String> vals,long reportId)throws Exception{File dir=new File(getFilesDir(),"reports");if(!dir.exists())dir.mkdirs();File file=new File(dir,"PathLIS_Report_"+reportId+".pdf");android.graphics.pdf.PdfDocument doc=new android.graphics.pdf.PdfDocument();android.graphics.pdf.PdfDocument.Page page=doc.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,1).create());Canvas c=page.getCanvas();Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.BLACK);paint.setTextSize(18);String lab=pref("lab_name","Maa PathLab");c.drawText(lab,40,50,paint);paint.setTextSize(10);c.drawText(pref("lab_address","Address not configured"),40,70,paint);c.drawLine(40,84,555,84,paint);paint.setTextSize(15);c.drawText("LABORATORY REPORT",40,112,paint);paint.setTextSize(11);c.drawText("Patient: "+p.name,40,140,paint);c.drawText("Age/Sex: "+p.age+" / "+p.sex,40,160,paint);c.drawText("Mobile: "+(p.phone.isEmpty()?"—":p.phone),40,180,paint);c.drawText("Ref. Doctor: "+(p.doctor.isEmpty()?"—":p.doctor),300,160,paint);c.drawText("Date: "+new SimpleDateFormat("dd-MM-yyyy HH:mm",Locale.getDefault()).format(new Date()),300,180,paint);paint.setTextSize(13);paint.setTypeface(Typeface.DEFAULT_BOLD);c.drawText(test,40,220,paint);paint.setTypeface(Typeface.DEFAULT);c.drawText("Parameter",40,250,paint);c.drawText("Result",270,250,paint);c.drawText("Unit",360,250,paint);c.drawText("Reference range",440,250,paint);c.drawLine(40,258,555,258,paint);int y=282;int printed=0;for(int i=0;i<ps.length;i++){String v=vals.get(i).trim();if(v.isEmpty())continue; // Do not print parameters for which no reading was entered.
            c.drawText(ps[i].name,40,y,paint);c.drawText(v,270,y,paint);c.drawText(ps[i].unit,360,y,paint);c.drawText(ps[i].low+"–"+ps[i].high,440,y,paint);try{double n=Double.parseDouble(v);if(n<ps[i].low||n>ps[i].high){paint.setColor(Color.RED);c.drawText("H/L",520,y,paint);paint.setColor(Color.BLACK);}}catch(Exception ignored){}y+=24;printed++;}paint.setTextSize(9);int footerY=Math.min(760,Math.max(710,y+28));c.drawText("Reference ranges are general guides; verify method-specific ranges and review results before release.",40,footerY,paint);paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(12);c.drawText("LT",510,footerY+42,paint);paint.setTypeface(Typeface.DEFAULT);doc.finishPage(page);FileOutputStream out=new FileOutputStream(file);doc.writeTo(out);out.close();doc.close();db.markReportPdf(reportId,file.getAbsolutePath());return file;}
    private void savePdf(){if(pendingPdf==null){toast("Generate a report first");return;}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/pdf");i.putExtra(Intent.EXTRA_TITLE,pendingPdf.getName());startActivityForResult(i,SAVE_PDF);}
    private void sharePdf(){if(pendingPdf==null){toast("Generate a report first");return;}try{Uri uri=FileProvider.getUriForFile(this,"com.pathlis.offline.fileprovider",pendingPdf);Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/pdf");i.putExtra(Intent.EXTRA_STREAM,uri);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Share laboratory report"));}catch(Exception e){toast("Sharing error: "+e.getMessage());}}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==SAVE_PDF&&res==RESULT_OK&&data!=null&&data.getData()!=null){try(InputStream in=new FileInputStream(pendingPdf);OutputStream out=getContentResolver().openOutputStream(data.getData())){byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);toast("PDF saved successfully");}catch(Exception e){toast("Could not save PDF: "+e.getMessage());}}}
    private void history(){syncPatientsFromCloud();syncReportsFromCloud();base("Report History");Cursor c=db.getReadableDatabase().rawQuery("SELECT r.id,p.name,r.test,r.created FROM reports r JOIN patients p ON p.id=r.patient_id ORDER BY r.id DESC",null);if(c.getCount()==0)add(text("No reports generated yet.",15,dark,false));while(c.moveToNext()){long id=c.getLong(0);String title=c.getString(1)+" — "+c.getString(2)+"\n"+c.getString(3);add(btn(title,()->{String path=db.reportPath(id);if(path==null||!new File(path).exists()){toast("PDF file unavailable. Generate the report again.");return;}pendingPdf=new File(path);new AlertDialog.Builder(this).setTitle("Report").setItems(new String[]{"Save PDF to Downloads","Share PDF"},(d,w)->{if(w==0)savePdf();else sharePdf();}).show();}));}c.close();add(btn("Back",this::home));}
    private void settings(){if(!isAdmin()){toast("Only Admin can change lab settings");home();return;}base("Lab Settings");EditText lab=field("Laboratory name");lab.setText(pref("lab_name","Maa PathLab"));EditText addr=field("Laboratory address");addr.setText(pref("lab_address",""));EditText tech=field("Technologist name");tech.setText(pref("tech_name",""));EditText td=field("Technologist designation");td.setText(pref("tech_designation","Lab Technician"));EditText path=field("Pathologist name (optional)");path.setText(pref("path_name",""));EditText pd=field("Pathologist designation");pd.setText(pref("path_designation","Pathologist"));add(lab);add(addr);add(tech);add(td);add(path);add(pd);add(btn("Save Settings",()->{getSharedPreferences("lab",0).edit().putString("lab_name",lab.getText().toString().trim()).putString("lab_address",addr.getText().toString().trim()).putString("tech_name",tech.getText().toString().trim()).putString("tech
