package com.hackadroid.datamodel;

import android.location.Location;

import java.util.Map;

import lombok.Data;

/**
 * POJO class for UserData stored in Firebase DB
 */
@Data
public class UserDataModel {
    private String _userId;
    private String _FullName;
    private String _emailId;
    private Map<String, Location> locations;
}
