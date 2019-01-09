package fr.isen.ibm.tri_dechets.Activity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.ibm.watson.developer_cloud.natural_language_classifier.v1.NaturalLanguageClassifier;
import com.ibm.watson.developer_cloud.natural_language_classifier.v1.model.Classification;
import com.ibm.watson.developer_cloud.service.security.IamOptions;
import com.ibm.watson.developer_cloud.visual_recognition.v3.VisualRecognition;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ClassifiedImages;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

import fr.isen.ibm.tri_dechets.R;

import static fr.isen.ibm.tri_dechets.util.Constants.SELECT_PICTURE;
import static fr.isen.ibm.tri_dechets.util.Constants.TAKE_PICTURE;

public class MainActivity extends AppCompatActivity {

    private boolean recordOn = false;
    private IamOptions optionsVC, optionsNLC;
    private VisualRecognition visualRecognition;
    private NaturalLanguageClassifier naturalLanguageClassifier;
    private ImageView analyzeImage;
    private EditText text;
    private Button recordAudio;
    private TextView resultView;
    private String file;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        analyzeImage = findViewById(R.id.imageView);
        text = findViewById(R.id.text);
        recordAudio = findViewById(R.id.send_sound);
        resultView = findViewById(R.id.recyclable);
        progressBar = findViewById(R.id.progressBar);
        recordOn = false;
        initIbm();
        askPermissions();
        initProgressBar();
    }

    private void initIbm() {
        optionsVC = new IamOptions.Builder()
                .apiKey(getString(R.string.visual_recognition_api_key))
                .build();
        visualRecognition =
                new VisualRecognition(getString(R.string.visual_recognition_version), optionsVC);
        optionsNLC = new IamOptions.Builder()
                .apiKey((getString(R.string.natural_language_classifier_api_key)))
                .build();
        naturalLanguageClassifier = new NaturalLanguageClassifier(optionsNLC);
    }

    private void askPermissions() {
        String[] permissions = {};
        ArrayList<String> permissionsList = new ArrayList();

        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
                !ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            permissionsList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
                !ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE))
            permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED &&
                !ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.RECORD_AUDIO))
            permissionsList.add(Manifest.permission.RECORD_AUDIO);

        if (permissionsList.size() > 0)
            ActivityCompat.requestPermissions(MainActivity.this, permissionsList.toArray(permissions), permissionsList.size());
    }

    private void initProgressBar() {
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.INVISIBLE);
    }

    public void sendTextToIbm(View v) {
        if (progressBar != null && !text.getText().toString().equals(""))
            progressBar.setVisibility(View.VISIBLE);

        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    com.ibm.watson.developer_cloud.natural_language_classifier.v1.model.ClassifyOptions classifyOptions
                            = new com.ibm.watson.developer_cloud.natural_language_classifier.v1.model.ClassifyOptions
                            .Builder()
                            .classifierId(getString(R.string.natural_language_classifier_classifier_id))
                            .text(text.getText().toString())
                            .build();
                    Classification result =  naturalLanguageClassifier.classify(classifyOptions).execute();

                    final String text = getCorrectText(getBestClasseText(new JSONObject(String.valueOf(result))));

                    resultView.post(new Runnable() {
                        public void run() {
                            if (progressBar != null)
                                progressBar.setVisibility(View.INVISIBLE);

                            resultView.setText(text);
                        }
                    });
                } catch (JSONException ignored) {
                }
            }
        });

        if (!text.getText().toString().equals(""))
            thread.start();
    }

    public void sendSoundToIbm(View v) {
        recordAudio.setBackground((recordOn) ?
                getDrawable(R.drawable.mic) : getDrawable(R.drawable.mic_off));

    }

    private void uploadToIbm() {
        if (progressBar != null)
            progressBar.setVisibility(View.VISIBLE);

        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    String[] tmp = file.split("/");
                    InputStream imagesStream = new FileInputStream(file);
                    com.ibm.watson.developer_cloud.visual_recognition.v3.model.ClassifyOptions classifyOptions
                            = new com.ibm.watson.developer_cloud.visual_recognition.v3.model.ClassifyOptions
                            .Builder()
                            .imagesFile(imagesStream)
                            .imagesFilename(tmp[tmp.length - 1])
                            .threshold((float) 0.6)
                            .classifierIds(Collections
                                    .singletonList(getString(R.string.visual_recognition_model)))
                            .build();
                    ClassifiedImages result = visualRecognition
                            .classify(classifyOptions).execute();

                    final String text = getCorrectText(getBestClasseImages(new JSONObject(String.valueOf(result))));

                    resultView.post(new Runnable() {
                        public void run() {
                            if (progressBar != null)
                                progressBar.setVisibility(View.INVISIBLE);

                            resultView.setText(text);
                        }
                    });
                } catch (FileNotFoundException | JSONException ignored) {
                }
            }
        });

        thread.start();
    }

    private String getCorrectText(String text) {
        if (text != null)
        switch (text) {
            case "NotRecyclable":
                return "Non recyclable.";
            case "Recyclable":
                return "Recyclable.";
            case "Black":
                return "Poubelle noire.";
            case "Blue":
                return "Poubelle bleue.";
            case "Green":
                return "Poubelle verte.";
            case "Yellow":
                return "Poubelle jaune";
            default:
                return "Indéterminé (" + text + ") !!!";
        }
        else
            return "Indéterminé !!!";
    }

    private String getBestClasseText(JSONObject result) {
        try {
            JSONObject current;
            int index = -1;
            double maxScore = 0;

            for (int i = 0; i < result.getJSONArray("classes").length(); i++) {
                current = result.getJSONArray("classes").getJSONObject(i);

                if (current.getDouble("confidence") > maxScore) {
                    index = i;
                    maxScore = current.getDouble("confidence");
                }
            }

            if (index != -1)
                return result.getJSONArray("classes").getJSONObject(index)
                        .getString("class_name");
            else
                return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getBestClasseImages(JSONObject result) {
        try {
            JSONObject current;
            int index = -1;
            double maxScore = 0;

            for (int i = 0; i < result.getJSONArray("images").getJSONObject(0)
                    .getJSONArray("classifiers").getJSONObject(0)
                    .getJSONArray("classes").length(); i++) {
                current = result.getJSONArray("images").getJSONObject(0)
                        .getJSONArray("classifiers").getJSONObject(0)
                        .getJSONArray("classes").getJSONObject(i);

                if (current.getDouble("score") > maxScore) {
                    index = i;
                    maxScore = current.getDouble("score");
                }
            }

            if (index != -1)
                return result.getJSONArray("images").getJSONObject(0)
                        .getJSONArray("classifiers").getJSONObject(0)
                        .getJSONArray("classes").getJSONObject(index)
                        .getString("class");
            else
                return null;
        } catch (Exception e) {
            return null;
        }
    }

    public void takePhoto(View v) {
        resultView.setText("");
        Intent camera = new Intent();
        camera.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(camera, TAKE_PICTURE);
    }

    public void openGallery(View v) {
        resultView.setText("");
        Intent gallery = new Intent();
        gallery.setType("image/*");
        gallery.setAction(Intent.ACTION_OPEN_DOCUMENT);
        startActivityForResult(gallery, SELECT_PICTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case SELECT_PICTURE:
                    Uri selectedImageUri = data.getData();
                    try {
                        Bitmap selectedImage = MediaStore.Images.Media.getBitmap(getApplicationContext().getContentResolver(), selectedImageUri);
                        analyzeImage.setImageBitmap(selectedImage);
                        file = getRealPathFromURI(getImageUri(getApplicationContext(), selectedImage));
                    } catch (IOException ignored) {
                    }
                    break;
                case TAKE_PICTURE:
                    Bitmap imageBitmap = (Bitmap) data.getExtras().get("data");
                    analyzeImage.setImageBitmap(imageBitmap);
                    file = getRealPathFromURI(getImageUri(getApplicationContext(), imageBitmap));
                    break;
            }
        }

        if (file != null)
            uploadToIbm();
    }


    public Uri getImageUri(Context inContext, Bitmap inImage) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(inContext.getContentResolver(), inImage, "Title", null);
        return Uri.parse(path);
    }

    public String getRealPathFromURI(Uri uri) {
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        Objects.requireNonNull(cursor).moveToFirst();
        int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
        return cursor.getString(idx);
    }
}
