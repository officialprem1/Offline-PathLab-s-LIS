    private void settings(){
        if(!isAdmin()){toast("Only Admin can change lab settings");home();return;}
        base("Lab Settings");
        EditText lab=field("Laboratory name"); lab.setText(pref("lab_name","Maa PathLab"));
        EditText addr=field("Laboratory address"); addr.setText(pref("lab_address",""));
        EditText tech=field("Technologist name"); tech.setText(pref("tech_name",""));
        EditText td=field("Technologist designation"); td.setText(pref("tech_designation","Lab Technician"));
        EditText path=field("Pathologist name (optional)"); path.setText(pref("path_name",""));
        EditText pd=field("Pathologist designation"); pd.setText(pref("path_designation","Pathologist"));
        add(lab); add(addr); add(tech); add(td); add(path); add(pd);
        add(btn("Save Settings",()->{
            getSharedPreferences("lab",0).edit()
                .putString("lab_name",lab.getText().toString().trim())
                .putString("lab_address",addr.getText().toString().trim())
                .putString("tech_name",tech.getText().toString().trim())
                .putString("tech_designation",td.getText().toString().trim())
                .putString("path_name",path.getText().toString().trim())
                .putString("path_designation",pd.getText().toString().trim())
                .apply();
            toast("Lab settings saved");
            home();
        }));
        add(btn("Back",this::home));
    }

    private String pref(String key,String fallback){
        return getSharedPreferences("lab",0).getString(key,fallback);
    }

    private void toast(String message){
        Toast.makeText(this,message,Toast.LENGTH_LONG).show();
    }

    private class DB extends SQLiteOpenHelper {
        DB(Context context){super(context,"pathlis.db",null,2);}

        @Override public void onCreate(SQLiteDatabase d){
            d.execSQL("CREATE TABLE patients (id INTEGER PRIMARY KEY AUTOINCREMENT, remote_id TEXT, lab_id TEXT, name TEXT, age TEXT, sex TEXT, phone TEXT, doctor TEXT, date TEXT)");
            d.execSQL("CREATE TABLE reports (id INTEGER PRIMARY KEY AUTOINCREMENT, remote_id TEXT, lab_id TEXT, patient_id INTEGER, test TEXT, results TEXT, created TEXT, pdf_path TEXT)");
        }

        @Override public void onUpgrade(SQLiteDatabase d,int oldVersion,int newVersion){
            if(oldVersion<2){
                try{d.execSQL("ALTER TABLE patients ADD COLUMN remote_id TEXT");}catch(Exception ignored){}
                try{d.execSQL("ALTER TABLE patients ADD COLUMN lab_id TEXT");}catch(Exception ignored){}
                try{d.execSQL("ALTER TABLE reports ADD COLUMN remote_id TEXT");}catch(Exception ignored){}
                try{d.execSQL("ALTER TABLE reports ADD COLUMN lab_id TEXT");}catch(Exception ignored){}
            }
        }

        Patient patient(long id){
            Cursor c=getReadableDatabase().rawQuery("SELECT id,remote_id,name,age,sex,phone,doctor,date FROM patients WHERE id=?",new String[]{String.valueOf(id)});
            try{
                if(!c.moveToFirst())return null;
                Patient p=new Patient();
                p.id=c.getLong(0);p.remoteId=c.getString(1);p.name=c.getString(2);
                p.age=c.getString(3);p.sex=c.getString(4);p.phone=c.getString(5);
                p.doctor=c.getString(6);p.date=c.getString(7);
                return p;
            }finally{c.close();}
        }

        long saveReport(long patientId,String test,Param[] params,ArrayList<String> values){
            JSONObject result=new JSONObject();
            try{
                for(int i=0;i<params.length;i++){
                    JSONObject item=new JSONObject();
                    item.put("value",values.get(i));item.put("unit",params[i].unit);
                    item.put("reference_low",params[i].low);item.put("reference_high",params[i].high);
                    result.put(params[i].name,item);
                }
            }catch(Exception ignored){}
            ContentValues cv=new ContentValues();
            cv.put("patient_id",patientId);cv.put("test",test);
            cv.put("results",result.toString());
            cv.put("created",new SimpleDateFormat("dd-MM-yyyy HH:mm",Locale.getDefault()).format(new Date()));
            return getWritableDatabase().insert("reports",null,cv);
        }

        void markReportPdf(long id,String path){
            ContentValues cv=new ContentValues();cv.put("pdf_path",path);
            getWritableDatabase().update("reports",cv,"id=?",new String[]{String.valueOf(id)});
        }

        String reportPath(long id){
            Cursor c=getReadableDatabase().rawQuery("SELECT pdf_path FROM reports WHERE id=?",new String[]{String.valueOf(id)});
            try{return c.moveToFirst()?c.getString(0):null;}finally{c.close();}
        }
    }
}
