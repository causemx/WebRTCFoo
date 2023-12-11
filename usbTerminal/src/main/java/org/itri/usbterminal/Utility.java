package org.itri.usbterminal;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.dronefleet.mavlink.MavlinkMessage;
import io.dronefleet.mavlink.common.Gps2Rtk;
import io.dronefleet.mavlink.common.RawImu;
import io.dronefleet.mavlink.protocol.MavlinkPacket;

public class Utility {

//    MavlinkPacket getMavlinkPacket(MavlinkMessage msg){
//        MavlinkPacket packet = null;
//
//        return packet;
//    }

    public JSONObject getRawImuJson(RawImu data) throws JSONException {
        JSONObject rawImu = new JSONObject();
        rawImu.put("timeUsec", data.timeUsec());
        rawImu.put("xacc", data.xacc());
        rawImu.put("yacc", data.yacc());
        rawImu.put("zacc", data.zacc());
        rawImu.put("xgyro", data.xgyro());
        rawImu.put("ygyro", data.ygyro());
        rawImu.put("zgyro", data.zgyro());
        rawImu.put("xmag", data.xmag());
        rawImu.put("ymag", data.ymag());
        rawImu.put("zmag", data.zmag());
        rawImu.put("id", data.id());
        rawImu.put("temperature", data.temperature());

        return rawImu;
    }

    public JSONObject getRtkGpsJson(Gps2Rtk data) throws JSONException {
        JSONObject rtkGps = new JSONObject();
        rtkGps.put("timeLastBaselineMs", data.timeLastBaselineMs());
        rtkGps.put("rtkReceiverId", data.rtkReceiverId());
        rtkGps.put("wn", data.wn());
        rtkGps.put("tow", data.tow());
        rtkGps.put("rtkHealth", data.rtkHealth());
        rtkGps.put("rtkRate", data.rtkRate());
        rtkGps.put("nsats", data.nsats());
        rtkGps.put("baselineCoordsType", data.baselineCoordsType());
        rtkGps.put("baselineAMm", data.baselineAMm());
        rtkGps.put("baselineBMm", data.baselineBMm());
        rtkGps.put("baselineCMm", data.baselineCMm());
        rtkGps.put("accuracy", data.accuracy());
        rtkGps.put("iarNumHypotheses", data.iarNumHypotheses());

        return rtkGps;
    }
}
