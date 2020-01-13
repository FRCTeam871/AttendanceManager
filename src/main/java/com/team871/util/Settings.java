package com.team871.util;

import com.team871.exception.RobotechException;
import com.team871.ui.LoginType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Settings {
    private static final Logger logger = LoggerFactory.getLogger(Settings.class);
    private static final Settings INSTANCE = new Settings();

    private final Properties props;
    private final List<Listener> listeners = new ArrayList<>();

    public interface Listener {
        void onDateChanged();
    }

    public static Settings getInstance() {
        return INSTANCE;
    }

    private Settings() {
        props = new Properties();
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public void setDate(String date) {
        props.setProperty("Date", date);
        listeners.forEach(Listener::onDateChanged);
    }

    public LocalDate getDate() {
        return Utils.getLocalDate(props.getProperty("Date"));
    }

    public String getJposXmlPath() {
        return props.getProperty("jposPath");
    }

    public Path getSheetPath() {
        return Paths.get(props.getProperty("worksheet"));
    }

    public boolean getFun() {
        return Boolean.getBoolean(props.getProperty("fun"));
    }

    public LoginType getLoginType(){
        return LoginType.valueOf(props.getProperty("loginType"));
    }

    public String getAttendanceSheet() {
        return props.getProperty("attendanceSheet");
    }

    public String getRosterSheet() {
        return props.getProperty("rosterSheet");
    }

    public int getRosterHeaderRow() {
        return Integer.parseInt(props.getProperty("roster.headerRow"));
    }

    public int getRosterFirstDataRow() {
        return Integer.parseInt(props.getProperty("roster.firstRow"));
    }

    public int getAttendanceHeaderRow() {
        return Integer.parseInt(props.getProperty("attendance.headerRow"));
    }

    public int getAttendanceFirstDataRow() {
        return Integer.parseInt(props.getProperty("attendance.firstRow"));
    }

    public String getMentorAttendanceSheet() {
        return props.getProperty("mentorSheet");
    }

    public String getMentorRosterSheet() {
        return props.getProperty("mentorRSheet");
    }

    public int getMentorRosterHeaderRow() {
        return Integer.parseInt(props.getProperty("mentorRos.headerRow"));
    }

    public int getMentorRosterFirstDataRow() {
        return Integer.parseInt(props.getProperty("mentorRos.firstRow"));
    }

    public int getMentorAttendanceHeaderRow() {
        return Integer.parseInt(props.getProperty("mentorAtt.headerRow"));
    }

    public int getMentorAttendanceFirstDataRow() {
        return Integer.parseInt(props.getProperty("mentorAtt.firstRow"));
    }

    public void init(String prefsFile) throws RobotechException {
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("M/d");

        props.setProperty("Date", LocalDate.now().format(fmt));
        props.setProperty("Fun", "true");

        final File file = new File(prefsFile);

        if(file.isDirectory()) {
            throw new RobotechException("File must not be a directory");
        }

        logger.info("Looking for prefs file at: " + file.getAbsolutePath());
        if (!file.exists()) {
            logger.error("Prefs file does not exist");
            throw new RobotechException("Preference file does not exist");
        }

        try(final FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
        } catch (IOException e) {
            throw new RobotechException("Unable to load preferences.", e);
        }
    }

    public static boolean isNullOrEmpty(String value) {
        return value == null || value.isEmpty();
    }

    public int getAttendanceFirstDataColumn() {
        return Integer.parseInt(props.getProperty("attendance.firstColumn"));
    }
}
