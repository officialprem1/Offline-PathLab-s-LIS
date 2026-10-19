package com.pathlis.offline;

import org.json.JSONArray;
import org.json.JSONObject;

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
}
