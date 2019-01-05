package ui;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Settings {

    private static String date;
    private static String jposXmlPath;
    private static String rawSheetURL;
    private static URL sheetURL;
    private static Mode mode;
    private static String sheet;
    private static boolean fun;

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
        Settings.rawSheetURL = sheetURL;
        if(sheetURL.startsWith("%RES%")){
            Settings.sheetURL = Settings.class.getClassLoader().getResource(sheetURL.substring(5));
        }else if(sheetURL.startsWith("%REL%")){
            try {
                File f = new File(new File(Settings.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile(), sheetURL.substring(5));

                Settings.sheetURL = f.toURI().toURL();
            }catch (URISyntaxException e) {
                e.printStackTrace();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }else {
            try{
                Settings.sheetURL = new URL(sheetURL);
            }catch(MalformedURLException e){
                e.printStackTrace();
            }
        }
    }

    public static Mode getMode(){
        return mode;
    }

    public static void setMode(Mode mode){
        Settings.mode = mode;
    }

    public static void setSheet(String sheet){
        Settings.sheet = sheet;
    }

    public static String getSheet(){
        return sheet;
    }

    public static void setFun(boolean fun){
        Settings.fun = fun;
    }

    public static boolean getFun() {
        return fun;
    }

    public static void init(){
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("M/d");
        setDate(LocalDate.now().format(fmt));
        setJposXmlPath("%HOME%\\jpos.xml");
        setSheetURL("%RES%att.xlsx");
        setMode(Mode.IN_ONLY);
        setSheet("Build Season");
        setFun(true);
    }

    public static Collection<? extends String> getDebugInfo(){
        List<String> ret = new ArrayList<>();
        ret.add("Date = \"" + getDate() + "\"");
        ret.add("jpos.xml Path = \"" + getJposXmlPath() + "\"");
        ret.add("Sheet URL = \"" + getSheetURL() + "\"");
        ret.add("Mode = " + Settings.getMode());
        ret.add("Sheet = " + Settings.getSheet());
        return ret;
    }

    public static boolean inJar(){
        if(Settings.class.getResource("Settings.class") == null) return false;
        return Settings.class.getResource("Settings.class").toString().startsWith("jar:");
    }

    public static File getDefaultPrefsFile() {
        try {
            return new File(inJar() ? new File(Settings.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile() : new File(System.getProperty("user.home")), "preferences.prefs");
        } catch (URISyntaxException e) {
            System.err.println("Could not get default preferences file.");
            e.printStackTrace();
        }
        return null;
    }

    public static void setPrefsFile(File file) {
        if(file == null) throw new IllegalArgumentException("file must not be null");
        if(file.isDirectory()) throw new IllegalArgumentException("file must not be a directory");

        System.out.println("Looking for prefs file at: " + file.getAbsolutePath());
        if (!file.exists()) {
            try {
                System.out.println("File does not exist. Creating default prefs file at " + file.getAbsolutePath());
                file.createNewFile();
                savePreferences(file);
            }catch(IOException e){
                System.err.println("Exception while creating default preferences file at " + file.getAbsolutePath());
                e.printStackTrace();
            }
        }

        try{
            loadPreferences(file);
        }catch(IOException e){
            System.err.println("Exception while loading preferences file at " + file.getAbsolutePath());
            e.printStackTrace();
        }

    }

    public static void savePreferences(File file) throws IOException {
        System.out.println("saving preferences to " + file.getAbsolutePath());
        try(BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write(rawSheetURL + "\n");
            bw.write(getJposXmlPath() + "\n");
            bw.write(mode.toString() + "\n");
            bw.write(getSheet() + "\n");
            bw.write(getFun() + "\n");
        }
    }

    public static void loadPreferences(File file) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(file));
        setSheetURL(br.readLine());
        setJposXmlPath(br.readLine());
        setMode(Mode.valueOf(br.readLine()));
        setSheet(br.readLine());
        setFun(Boolean.parseBoolean(br.readLine()));
        br.close();
    }

}
