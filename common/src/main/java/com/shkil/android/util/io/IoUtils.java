package com.shkil.android.util.io;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.WillClose;

public class IoUtils {

    @WillClose
    public static DirectByteBuffer readFully(InputStream inputStream) throws IOException {
        try {
            DirectByteArrayOutputStream outputStream = new DirectByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            return outputStream;
        } finally {
            inputStream.close();
        }
    }

    @Nullable
    public static byte[] marshallParcelable(@Nullable Parcelable object) {
        if (object == null) {
            return null;
        }
        Parcel parcel = Parcel.obtain();
        try {
            object.writeToParcel(parcel, 0);
            return parcel.marshall();
        } finally {
            parcel.recycle();
        }
    }

    @Nullable
    public static <V extends Parcelable> V unmarshallParcelable(@Nullable byte[] data, Parcelable.Creator<V> creator) {
        if (data == null) {
            return null;
        }
        Parcel parcel = Parcel.obtain();
        try {
            parcel.unmarshall(data, 0, data.length);
            parcel.setDataPosition(0);
            return creator.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }

    @NonNull
    public static byte[] marshallParcelableTypedList(@Nullable List<? extends Parcelable> list) {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeTypedList(list);
            return parcel.marshall();
        } finally {
            parcel.recycle();
        }
    }

    @Nullable
    public static <V> ArrayList<V> unmarshallParcelableTypedList(@Nullable byte[] data, Parcelable.Creator<V> creator) {
        if (data == null) {
            return null;
        }
        Parcel parcel = Parcel.obtain();
        try {
            parcel.unmarshall(data, 0, data.length);
            parcel.setDataPosition(0);
            return parcel.createTypedArrayList(creator);
        } finally {
            parcel.recycle();
        }
    }
}
