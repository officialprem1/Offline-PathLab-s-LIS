package com.pathlis.offline;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class SupabaseClient {

    public static final String BASE_URL =
            "https://cgtlwgpjmfnovquoboxm.supabase.co";

    public static final String PUBLISHABLE_KEY =
            "sb_publishable_1ZsZ00a9yPfqyQu3TBT0zw_NS8MRtv-";

    public String accessToken;
    public String userId;
    public String labId;
    public String role;
    public String fullName;

    private JSONObject request(
            String method,
            String path,
            JSONObject body,
            boolean authRequired,
            String prefer
    ) throws Exception {

        URL url = new URL(BASE_URL + path);
        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        connection.setRequestMethod(method);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("apikey", PUBLISHABLE_KEY);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept-Profile", "public");
        connection.setRequestProperty("Content-Profile", "public");

        if (authRequired) {
            if (accessToken == null || accessToken.isEmpty()) {
                connection.disconnect();
                throw new IOException("Please login again.");
            }
            connection.setRequestProperty(
                    "Authorization", "Bearer " + accessToken);
        }

        if (prefer != null) {
            connection.setRequestProperty("Prefer", prefer);
        }

        if (body != null) {
            connection.setDoOutput(true);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }

        int code = connection.getResponseCode();
        InputStream input = (code >= 200 && code < 300)
                ? connection.getInputStream()
                : connection.getErrorStream();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        if (input != null) {
            byte[] bytes = new byte[4096];
            int count;
            while ((count = input.read(bytes)) != -1) {
                buffer.write(bytes, 0, count);
            }
            input.close();
        }

        String response = new String(
                buffer.toByteArray(), StandardCharsets.UTF_8);

        connection.disconnect();

        if (code < 200 || code >= 300) {
            throw new IOException(
                    "Supabase HTTP " + code + ": " + response);
        }

        if (response.trim().isEmpty()) {
            return new JSONObject();
        }

        if (response.trim().startsWith("[")) {
            JSONObject wrapper = new JSONObject();
            wrapper.put("data", new JSONArray(response));
            return wrapper;
        }

        return new JSONObject(response);
    }

    public void login(String email, String password) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("email", email.trim());
        payload.put("password", password);

        JSONObject auth = request(
                "POST",
                "/auth/v1/token?grant_type=password",
                payload,
                false,
                null
        );

        accessToken = auth.getString("access_token");
        userId = auth.getJSONObject("user").getString("id");

        JSONObject profileResponse = request(
                "GET",
                "/rest/v1/profiles?id=eq." + userId
                        + "&select=id,lab_id,role,full_name",
                null,
                true,
                null
        );

        JSONArray profiles = profileResponse.optJSONArray("data");

        if (profiles == null || profiles.length() == 0) {
            clearSession();
            throw new IOException(
                    "Lab profile missing. Check public.profiles.");
        }

        JSONObject profile = profiles.getJSONObject(0);

        labId = profile.optString("lab_id", "");
        role = profile.optString("role", "");
        fullName = profile.optString("full_name", email);

        if (labId.isEmpty() || "null".equals(labId)) {
            clearSession();
            throw new IOException("Account is not linked to a lab.");
        }

        if (!"admin".equals(role) && !"staff".equals(role)) {
            clearSession();
            throw new IOException("Invalid account role.");
        }
    }

    public void clearSession() {
        accessToken = null;
        userId = null;
        labId = null;
        role = null;
        fullName = null;
    }

    public JSONArray getPatients() throws Exception {
        JSONObject response = request(
                "GET",
                "/rest/v1/patients?lab_id=eq." + labId
                        + "&select=id,lab_id,name,age,sex,phone,doctor,created_at,created_by"
                        + "&order=created_at.desc",
                null,
                true,
                null
        );

        return response.optJSONArray("data");
    }

    public JSONObject insertPatient(
            String name,
            String age,
            String sex,
            String phone,
            String doctor
    ) throws Exception {

        JSONObject payload = new JSONObject();
        payload.put("lab_id", labId);
        payload.put("name", name);
        payload.put("age", age);
        payload.put("sex", sex);
        payload.put("phone", phone);
        payload.put("doctor", doctor);
        payload.put("created_by", userId);

        JSONObject response = request(
                "POST",
                "/rest/v1/patients?select=*",
                payload,
                true,
                "return=representation"
        );

        JSONArray rows = response.optJSONArray("data");

        if (rows == null || rows.length() == 0) {
            throw new IOException("Patient was not returned by Supabase.");
        }

        return rows.getJSONObject(0);
    }

    public JSONArray getReports() throws Exception {
        JSONObject response = request(
                "GET",
                "/rest/v1/reports?lab_id=eq." + labId
                        + "&select=id,lab_id,patient_id,test,results,created_at,created_by"
                        + "&order=created_at.desc",
                null,
                true,
                null
        );

        return response.optJSONArray("data");
    }

    public JSONObject insertReport(
            String patientId,
            String test,
            JSONObject results
    ) throws Exception {

        JSONObject payload = new JSONObject();
        payload.put("lab_id", labId);
        payload.put("patient_id", patientId);
        payload.put("test", test);
        payload.put("results", results);
        payload.put("created_by", userId);

        JSONObject response = request(
                "POST",
                "/rest/v1/reports?select=*",
                payload,
                true,
                "return=representation"
        );

        JSONArray rows = response.optJSONArray("data");

        if (rows == null || rows.length() == 0) {
            throw new IOException("Report was not returned by Supabase.");
        }

        return rows.getJSONObject(0);
    }
}
