package com.shkil.android.util.io;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.IOException;
import java.io.InputStream;

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

    public static byte[] parcelableToBytes(Parcelable object) {
        Parcel parcel = Parcel.obtain();
        try {
            object.writeToParcel(parcel, 0);
            return parcel.marshall();
        } finally {
            parcel.recycle();
        }
    }

    public static <V extends Parcelable> V createParcelable(byte[] data, Parcelable.Creator<V> creator) {
        Parcel parcel = Parcel.obtain();
        parcel.unmarshall(data, 0, data.length);
        return creator.createFromParcel(parcel);
    }
}
