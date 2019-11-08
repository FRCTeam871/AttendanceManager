package com.team871.data;

import java.util.Map;
import java.util.Set;

public class Student {
    private final String firstName;
    private final String lastName;

    private long id;
    private Subteam subteam;
    private int grade;
    private SafeteyFormState safeteyFormState;
    private FirstRegistration registration;

    Map<String, AttendanceItem> attendance;

    public Student(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }
}
