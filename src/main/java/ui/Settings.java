package ui;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class Settings {

    private static String date;
    private static String jposXmlPath;
    private static URL sheetURL;

    public static String getDate() {
        return date;
    }

    public static void setDate(String date) {
        Settings.date = date;
    }

    public static String getJposXmlPath() {
        return jposXmlPath;
    }

    public static void setJposXmlPath(String jposXmlPath) {
        Settings.jposXmlPath = jposXmlPath.replace("%HOME%", System.getProperty("user.home"));
    }

    public static URL getSheetURL() {
        return sheetURL;
    }

    public static void setSheetURL(URL sheetURL) {
        Settings.sheetURL = sheetURL;
    }

    public static void setSheetURL(String sheetURL) {

        if(sheetURL.startsWith("%RES%")){
            Settings.sheetURL = Settings.class.getClassLoader().getResource(sheetURL.substring(5));
        }else {
            try{
                Settings.sheetURL = new URL(sheetURL);
            }catch(MalformedURLException e){
                e.printStackTrace();
            }
        }
    }

    public static void init(){
        setDate("10/25");
        setJposXmlPath("%HOME%\\jpos.xml");
        setSheetURL("%RES%att.xlsx");
    }

    public static Collection<? extends String> getDebugInfo(){
        List<String> ret = new ArrayList<>();
        ret.add("Date = \"" + getDate() + "\"");
        ret.add("jpos.xml Path = \"" + getJposXmlPath() + "\"");
        ret.add("Sheet URL = \"" + getSheetURL() + "\"");
        return ret;
    }

}
