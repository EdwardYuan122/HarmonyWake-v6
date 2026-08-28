package com.oai.harmonywake;

import org.json.JSONException;
import org.json.JSONObject;

public class WakeTask {
    public int id, hour, minute, screenMinutes;
    public String packageName, appName;
    public boolean enabled;

    public WakeTask(int id, int hour, int minute, String packageName, String appName,
                    int screenMinutes, boolean enabled) {
        this.id=id; this.hour=hour; this.minute=minute; this.packageName=packageName;
        this.appName=appName; this.screenMinutes=screenMinutes; this.enabled=enabled;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o=new JSONObject();
        o.put("id",id); o.put("hour",hour); o.put("minute",minute);
        o.put("packageName",packageName==null?"":packageName);
        o.put("appName",appName==null?"":appName);
        o.put("screenMinutes",screenMinutes); o.put("enabled",enabled); return o;
    }

    public static WakeTask fromJson(JSONObject o) throws JSONException {
        return new WakeTask(o.getInt("id"),o.getInt("hour"),o.getInt("minute"),
                o.optString("packageName",""),o.optString("appName",""),
                o.optInt("screenMinutes",10),o.optBoolean("enabled",true));
    }
}
