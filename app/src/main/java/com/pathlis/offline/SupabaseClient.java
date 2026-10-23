package com.pathlis.offline;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class SupabaseClient {

    private static final String SUPABASE_URL =
            "https://cgtlwgpjmfnovquoboxm.supabase.co";

    private static final String API_KEY =
            "sb_publishable_1ZsZ00a9yPfqyQu3TBT0zw_NS8MRtv-";

    public String accessToken = "";
    public String userId = "";
    public String labId = "";
    public String role = "";
    public String fullName = "";

    public SupabaseClient() {
    }

    private String request(
            String method,
            String path,
            JSONObject body,
            boolean authenticated
    ) throws Exception {

        HttpURLConnection conn = null;

        try {
            URL url = new URL(SUPABASE_URL + path);
            conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod(method);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);

            conn.setRequestProperty("apikey", API_KEY);
            conn.setRequestProperty("Accept", "application/json");

            if (authenticated) {
                if (accessToken == null || accessToken.isEmpty()) {
                    throw new Exception("Please login again.");
                }

                conn.setRequestProperty(
                        "Authorization",
                        "Bearer " + accessToken
                );
            } else {
                conn.setRequestProperty(
                        "Authorization",
                        "Bearer " + API_KEY
                );
            }

            if (body != null) {
                conn.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=utf-8"
                );

                conn.setRequestProperty(
                        "Prefer",
                        "return=representation"
                );

                conn.setDoOutput(true);

                byte[] bytes = body.toString()
                        .getBytes(StandardCharsets.UTF_8);

                try (OutputStream out = conn.getOutputStream()) {
                    out.write(bytes);
                }
            }

            int status = conn.getResponseCode();

            InputStream stream = status >= 200 && status < 300
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            String response = stream == null ? "" : readAll(stream);

            if (status < 200 || status >= 300) {
                String message = response;

                try {
                    JSONObject error = new JSONObject(response);
                    message = error.optString("message", response);

                    if (message.isEmpty()) {
                        message = error.optString(
                                "msg",
                                error.optString(
                                        "error_description",
                                        response
                                )
                        );
                    }
                } catch (Exception ignored) {
                }

                throw new Exception(
                        "Supabase HTTP " + status + ": " + message
                );
            }

            return response;

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String readAll(InputStream in) throws Exception {
        try (InputStream input = in;
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int n;

            while ((n = input.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }

            return out.toString("UTF-8");
        }
    }

    public void login(String email, String password) throws Exception {

        JSONObject payload = new JSONObject();
        payload.put("email", email.trim());
        payload.put("password", password);

        String authText = request(
                "POST",
                "/auth/v1/token?grant_type=password",
                payload,
                false
        );

        JSONObject auth = new JSONObject(authText);

        accessToken = auth.getString("access_token");
        userId = auth.getJSONObject("user").getString("id");

        String profilePath =
                "/rest/v1/profiles?id=eq." + userId
                + "&select=id,lab_id,full_name,role";

        JSONArray profiles = new JSONArray(
                request("GET", profilePath, null, true)
        );

        if (profiles.length() == 0) {
            clearSession();
            throw new Exception(
                    "Is account ka lab profile nahi mila."
            );
        }

        JSONObject profile = profiles.getJSONObject(0);

        labId = profile.optString("lab_id", "");
        role = profile.optString("role", "");
        fullName = profile.optString("full_name", "");

        if (labId.isEmpty() || "null".equals(labId)) {
            clearSession();
            throw new Exception(
                    "Account kisi lab se linked nahi hai."
            );
        }

        if (!"admin".equals(role) && !"staff".equals(role)) {
            clearSession();
            throw new Exception("Invalid account role.");
        }
    }

    public void clearSession() {
        accessToken = "";
        userId = "";
        labId = "";
        role = "";
        fullName = "";
    }

    public JSONArray getPatients() throws Exception {

        String path =
                "/rest/v1/patients?lab_id=eq." + labId
                + "&select=id,lab_id,name,age,sex,phone,doctor,"
                + "created_at,created_by"
                + "&order=created_at.desc";

        return new JSONArray(
                request("GET", path, null, true)
        );
    }

    public JSONObject insertPatient(
            String name,
            String age,
            String sex,
            String phone,
            String doctor
    ) throws Exception {

        JSONObject body = new JSONObject();

        body.put("lab_id", labId);
        body.put("name", name);
        body.put("age", age);
        body.put("sex", sex);
        body.put("phone", phone);
        body.put("doctor", doctor);
        body.put("created_by", userId);

        String response = request(
                "POST",
                "/rest/v1/patients?select=*",
                body,
                true
        );

        JSONArray rows = new JSONArray(response);

        if (rows.length() == 0) {
            throw new Exception(
                    "Patient save hua, lekin server ne record return nahi kiya."
            );
        }

        return rows.getJSONObject(0);
    }

    public JSONArray getReports() throws Exception {

        String path =
                "/rest/v1/reports?lab_id=eq." + labId
                + "&select=id,lab_id,patient_id,test,results,"
                + "created_at,created_by"
                + "&order=created_at.desc";

        return new JSONArray(
                request("GET", path, null, true)
        );
    }

    public JSONObject insertReport(
            String patientId,
            String test,
            JSONObject results
    ) throws Exception {

        JSONObject body = new JSONObject();

        body.put("lab_id", labId);
        body.put("patient_id", patientId);
        body.put("test", test);
        body.put("results", results);
        body.put("created_by", userId);

        String response = request(
                "POST",
                "/rest/v1/reports?select=*",
                body,
                true
        );

        JSONArray rows = new JSONArray(response);

        if (rows.length() == 0) {
            throw new Exception(
                    "Report save hua, lekin server ne record return nahi kiya."
            );
        }

        return rows.getJSONObject(0);
    }
    public JSONObject getLabSettings() throws Exception {
        String path = "/rest/v1/lab_settings?lab_id=eq." + URLEncoder.encode(labId, "UTF-8") + "&select=lab_id,lab_name,lab_address,tech_name,tech_designation,path_name,path_designation,config_version,updated_at";
        JSONArray rows = new JSONArray(request("GET", path, null, true));
        return rows.length() == 0 ? null : rows.getJSONObject(0);
    }

    public JSONObject saveLabSettings(JSONObject values) throws Exception {
        if (!"admin".equals(role)) throw new Exception("Admin role required.");
        values.put("lab_id", labId);
        values.put("updated_by", userId);
        String path = "/rest/v1/lab_settings?on_conflict=lab_id&select=*";
        HttpURLConnection conn = null;
        try {
            URL url = new URL(SUPABASE_URL + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST"); conn.setConnectTimeout(15000); conn.setReadTimeout(20000);
            conn.setRequestProperty("apikey", API_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates,return=representation");
            conn.setDoOutput(true);
            try (OutputStream out = conn.getOutputStream()) { out.write(values.toString().getBytes(StandardCharsets.UTF_8)); }
            int status = conn.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
            String response = stream == null ? "" : readAll(stream);
            if (status < 200 || status >= 300) throw new Exception("Supabase HTTP " + status + ": " + response);
            JSONArray rows = new JSONArray(response);
            if (rows.length() == 0) throw new Exception("Server did not return saved settings.");
            return rows.getJSONObject(0);
        } finally { if (conn != null) conn.disconnect(); }
    }

    /** Fetch all test definitions for the authenticated lab. RLS is the authority. */
    public JSONArray getLabTests(boolean includeInactive) throws Exception {
        String path = "/rest/v1/lab_tests?lab_id=eq." + URLEncoder.encode(labId, "UTF-8")
                + "&select=id,lab_id,test_name,sort_order,is_active,updated_at"
                + (includeInactive ? "" : "&is_active=eq.true")
                + "&order=sort_order.asc,test_name.asc";
        JSONArray tests = new JSONArray(request("GET", path, null, true));
        for (int i = 0; i < tests.length(); i++) {
            JSONObject test = tests.getJSONObject(i);
            String testId = test.getString("id");
            String paramsPath = "/rest/v1/lab_test_parameters?lab_id=eq."
                    + URLEncoder.encode(labId, "UTF-8") + "&test_id=eq."
                    + URLEncoder.encode(testId, "UTF-8")
                    + "&select=id,parameter_name,unit,reference_low,reference_high,age_sex_ranges,sort_order,is_active,updated_at"
                    + (includeInactive ? "" : "&is_active=eq.true")
                    + "&order=sort_order.asc,parameter_name.asc";
            test.put("parameters", new JSONArray(request("GET", paramsPath, null, true)));
        }
        return tests;
    }

    public JSONObject createLabTest(String name, int sortOrder) throws Exception {
        requireAdmin();
        JSONObject body = new JSONObject();
        body.put("lab_id", labId); body.put("test_name", name.trim());
        body.put("sort_order", sortOrder); body.put("is_active", true); body.put("updated_by", userId);
        JSONArray rows = new JSONArray(request("POST", "/rest/v1/lab_tests?select=*", body, true));
        if (rows.length() == 0) throw new Exception("Server did not return the new test.");
        return rows.getJSONObject(0);
    }

    public JSONObject createLabParameter(String testId, String name, String unit,
                                         Double low, Double high, int sortOrder) throws Exception {
        requireAdmin();
        JSONObject body = new JSONObject();
        body.put("lab_id", labId); body.put("test_id", testId);
        body.put("parameter_name", name.trim()); body.put("unit", unit.trim());
        if (low != null) body.put("reference_low", low);
        if (high != null) body.put("reference_high", high);
        body.put("sort_order", sortOrder); body.put("is_active", true); body.put("updated_by", userId);
        JSONArray rows = new JSONArray(request("POST", "/rest/v1/lab_test_parameters?select=*", body, true));
        if (rows.length() == 0) throw new Exception("Server did not return the new parameter.");
        return rows.getJSONObject(0);
    }

    public void updateLabTest(String id, String name, int sortOrder) throws Exception {
        requireAdmin();
        JSONObject body = new JSONObject(); body.put("test_name", name.trim()); body.put("sort_order", sortOrder); body.put("updated_by", userId);
        patchConfiguration("lab_tests", id, body);
    }

    public void updateLabParameter(String id, String name, String unit, Double low, Double high, int sortOrder) throws Exception {
        requireAdmin();
        JSONObject body = new JSONObject(); body.put("parameter_name", name.trim()); body.put("unit", unit.trim());
        body.put("reference_low", low == null ? JSONObject.NULL : low);
        body.put("reference_high", high == null ? JSONObject.NULL : high);
        body.put("sort_order", sortOrder); body.put("updated_by", userId);
        patchConfiguration("lab_test_parameters", id, body);
    }

    private void patchConfiguration(String table, String id, JSONObject body) throws Exception {
        if (!"lab_tests".equals(table) && !"lab_test_parameters".equals(table)) throw new Exception("Unsupported configuration table.");
        if (accessToken == null || accessToken.isEmpty()) throw new Exception("Please login again.");
        String url = SUPABASE_URL + "/rest/v1/" + table + "?id=eq." + URLEncoder.encode(id, "UTF-8")
                + "&lab_id=eq." + URLEncoder.encode(labId, "UTF-8");
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body.toString());
        Request req = new Request.Builder().url(url).patch(requestBody).addHeader("apikey", API_KEY)
                .addHeader("Authorization", "Bearer " + accessToken).addHeader("Accept", "application/json")
                .addHeader("Prefer", "return=representation").build();
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build();
        try (Response response = client.newCall(req).execute()) {
            String text = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) throw new Exception("Supabase HTTP " + response.code() + ": " + text);
            if (text.trim().isEmpty() || new JSONArray(text).length() == 0) throw new Exception("No configuration row was updated.");
        }
    }

    public void setConfigurationActive(String table, String id, boolean active) throws Exception {
        requireAdmin();
        if (!"lab_tests".equals(table) && !"lab_test_parameters".equals(table))
            throw new Exception("Unsupported configuration table.");
        JSONObject body = new JSONObject(); body.put("is_active", active); body.put("updated_by", userId);
        patchConfiguration(table, id, body);
    }

    private void requireAdmin() throws Exception {
        if (!"admin".equals(role) || accessToken == null || accessToken.isEmpty())
            throw new Exception("Admin role required.");
    }

    public JSONArray getActivity() throws Exception {
        if (!"admin".equals(role)) throw new Exception("Admin role required.");
        String path = "/rest/v1/lab_activity?lab_id=eq." + URLEncoder.encode(labId, "UTF-8")
                + "&select=id,lab_id,actor_id,action,target_type,target_id,created_at&order=created_at.desc&limit=100";
        return new JSONArray(request("GET", path, null, true));
    }

    public void logActivity(String action, String targetType, String targetId) {
        try {
            JSONObject body = new JSONObject(); body.put("lab_id", labId); body.put("actor_id", userId);
            body.put("action", action); body.put("target_type", targetType);
            if (targetId != null && !targetId.isEmpty()) body.put("target_id", targetId);
            request("POST", "/rest/v1/lab_activity?select=id", body, true);
        } catch (Exception ignored) { /* Activity logging must not block a clinical workflow. */ }
    }

}
