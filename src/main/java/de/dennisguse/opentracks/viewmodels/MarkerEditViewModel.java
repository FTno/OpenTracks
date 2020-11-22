package de.dennisguse.opentracks.viewmodels;

import android.app.Application;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.io.FileDescriptor;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.content.data.Marker;
import de.dennisguse.opentracks.content.data.Track;
import de.dennisguse.opentracks.content.provider.ContentProviderUtils;
import de.dennisguse.opentracks.services.TrackRecordingServiceConnection;
import de.dennisguse.opentracks.util.FileUtils;

public class MarkerEditViewModel extends AndroidViewModel {

    private static final String TAG = MarkerEditViewModel.class.getSimpleName();

    private MutableLiveData<Marker> markerData;
    private boolean isNewMarker;
    private Uri photoOriginalUri;
    private final TrackRecordingServiceConnection trackRecordingServiceConnection = new TrackRecordingServiceConnection();

    public MarkerEditViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<Marker> getMarkerData(@NonNull Track.Id trackId, @Nullable Marker.Id markerId) {
        if (markerData == null) {
            markerData = new MutableLiveData<>();
            trackRecordingServiceConnection.startConnection(getApplication());
            loadData(trackId, markerId);
        }
        return markerData;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        trackRecordingServiceConnection.unbind(getApplication());
    }

    private void loadData(Track.Id trackId, Marker.Id markerId) {
        Marker marker;
        isNewMarker = markerId == null;
        if (isNewMarker) {
            int nextMarkerNumber = trackId == null ? -1 : new ContentProviderUtils(getApplication()).getNextMarkerNumber(trackId);
            if (nextMarkerNumber == -1) {
                nextMarkerNumber = 0;
            }
            marker = new Marker(trackId);
            marker.setId(markerId);
            marker.setName(getApplication().getString(R.string.marker_name_format, nextMarkerNumber));
        } else {
            marker = new ContentProviderUtils(getApplication()).getMarker(markerId);
            if (marker.hasPhoto()) {
                photoOriginalUri = marker.getPhotoURI();
            }
        }
        markerData.postValue(marker);
    }

    private void deletePhoto(Uri photoUri) {
        File photoFile = FileUtils.getPhotoFileIfExists(getApplication(), markerData.getValue().getTrackId(), photoUri);
        FileUtils.deleteDirectoryRecurse(photoFile);
    }

    public void onPhotoDelete() {
        Marker marker = markerData.getValue();
        if (marker == null) {
            return;
        }

        if (marker.hasPhoto()) {
            if (!marker.getPhotoURI().equals(photoOriginalUri)) {
                deletePhoto(marker.getPhotoURI());
            }
            marker.setPhotoUrl(null);
            markerData.postValue(marker);
        }
    }

    public void onNewCameraPhoto(@NonNull Uri photoUri) {
        Marker marker = markerData.getValue();
        if (marker == null) {
            return;
        }

        marker.setPhotoUrl(photoUri.toString());
        markerData.postValue(marker);
    }

    public void onNewGalleryPhoto(@NonNull Uri srcUri) {
        Marker marker = markerData.getValue();
        if (marker == null) {
            return;
        }

        try (ParcelFileDescriptor parcelFd = getApplication().getContentResolver().openFileDescriptor(srcUri, "r")) {
            FileDescriptor srcFd = parcelFd.getFileDescriptor();
            File dstFile = new File(FileUtils.getImageUrl(getApplication(), marker.getTrackId()));
            FileUtils.copy(srcFd, dstFile);

            Uri photoUri = FileUtils.getUriForFile(getApplication(), dstFile);
            marker.setPhotoUrl(photoUri.toString());
            markerData.postValue(marker);
        } catch(Exception e) {
            Log.e(TAG, e.getMessage());
            Toast.makeText(getApplication(), R.string.marker_add_canceled, Toast.LENGTH_LONG).show();
        }
    }

    private void onAddDone(@NonNull Marker marker, String name, String category, String description) {
        trackRecordingServiceConnection.addMarker(getApplication(), name, category, description, marker.hasPhoto() ? marker.getPhotoURI().toString() : null);
    }

    private void onSaveDone(@NonNull Marker marker, String name, String category, String description) {
        marker.setName(name);
        marker.setCategory(category);
        marker.setDescription(description);
        new ContentProviderUtils(getApplication()).updateMarker(getApplication(), marker);

        if (photoOriginalUri != null && (!marker.hasPhoto() || !photoOriginalUri.equals(marker.getPhotoURI()))) {
            deletePhoto(photoOriginalUri);
        }
    }

    public void onDone(String name, String category, String description) {
        Marker marker = markerData.getValue();
        if (marker == null) {
            return;
        }

        if (isNewMarker) {
            onAddDone(marker, name, category, description);
        } else {
            onSaveDone(marker, name, category, description);
        }
    }

    public void onCancel() {
        Marker marker = markerData.getValue();
        if (marker == null) {
            return;
        }

        if (isNewMarker) {
            if (marker.hasPhoto()) {
                deletePhoto(marker.getPhotoURI());
            }
            if (photoOriginalUri != null && !photoOriginalUri.equals(marker.getPhotoURI())) {
                deletePhoto(photoOriginalUri);
            }
        } else if (photoOriginalUri != null) {
            if (marker.hasPhoto() && !marker.getPhotoURI().equals(photoOriginalUri)) {
                deletePhoto(marker.getPhotoURI());
            }
        } else if (marker.hasPhoto()) {
            deletePhoto(marker.getPhotoURI());
        }
    }
}
