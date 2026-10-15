package com.billing.gst;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** GST state / union territory codes (the first two characters of every GSTIN). */
public final class GstStates {

    public record State(String code, String name) {
    }

    private static final List<State> STATES = List.of(
            new State("01", "Jammu & Kashmir"),
            new State("02", "Himachal Pradesh"),
            new State("03", "Punjab"),
            new State("04", "Chandigarh"),
            new State("05", "Uttarakhand"),
            new State("06", "Haryana"),
            new State("07", "Delhi"),
            new State("08", "Rajasthan"),
            new State("09", "Uttar Pradesh"),
            new State("10", "Bihar"),
            new State("11", "Sikkim"),
            new State("12", "Arunachal Pradesh"),
            new State("13", "Nagaland"),
            new State("14", "Manipur"),
            new State("15", "Mizoram"),
            new State("16", "Tripura"),
            new State("17", "Meghalaya"),
            new State("18", "Assam"),
            new State("19", "West Bengal"),
            new State("20", "Jharkhand"),
            new State("21", "Odisha"),
            new State("22", "Chhattisgarh"),
            new State("23", "Madhya Pradesh"),
            new State("24", "Gujarat"),
            new State("26", "Dadra & Nagar Haveli and Daman & Diu"),
            new State("27", "Maharashtra"),
            new State("29", "Karnataka"),
            new State("30", "Goa"),
            new State("31", "Lakshadweep"),
            new State("32", "Kerala"),
            new State("33", "Tamil Nadu"),
            new State("34", "Puducherry"),
            new State("35", "Andaman & Nicobar Islands"),
            new State("36", "Telangana"),
            new State("37", "Andhra Pradesh"),
            new State("38", "Ladakh"),
            new State("97", "Other Territory"),
            new State("99", "Centre Jurisdiction"));

    private static final Map<String, State> BY_CODE = new LinkedHashMap<>();

    static {
        for (State s : STATES) {
            BY_CODE.put(s.code(), s);
        }
    }

    private GstStates() {
    }

    public static List<State> all() {
        return STATES;
    }

    public static boolean isValid(String code) {
        return code != null && BY_CODE.containsKey(code);
    }

    /** "Bihar (10)" style label, or the code itself if unknown. */
    public static String label(String code) {
        State s = code == null ? null : BY_CODE.get(code);
        return s == null ? (code == null ? "" : code) : s.name() + " (" + s.code() + ")";
    }
}
