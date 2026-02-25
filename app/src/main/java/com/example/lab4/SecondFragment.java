package com.example.lab4;



import android.content.Context;

import android.content.Intent;

import android.content.SharedPreferences;

import android.net.Uri;

import android.os.Bundle;

import android.os.Handler;

import android.os.Looper;

import android.view.LayoutInflater;

import android.view.View;

import android.view.ViewGroup;

import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;

import androidx.activity.result.contract.ActivityResultContracts;

import androidx.annotation.NonNull;

import androidx.annotation.Nullable;

import androidx.appcompat.app.AlertDialog;

import androidx.core.view.GravityCompat;

import androidx.drawerlayout.widget.DrawerLayout;

import androidx.fragment.app.Fragment;

import androidx.media3.common.MediaItem;

import androidx.media3.common.Player;

import androidx.media3.exoplayer.ExoPlayer;

import androidx.media3.ui.PlayerView;



import java.util.ArrayList;

import java.util.HashSet;

import java.util.List;

import java.util.Set;



public class SecondFragment extends Fragment {



    private ExoPlayer player;

    private PlayerView videoView;

    private final List<Uri> audioUris = new ArrayList<>();

    private final List<Uri> videoUris = new ArrayList<>();

    private final List<String> displayList = new ArrayList<>();

    private ArrayAdapter<String> adapter;

    private SharedPreferences prefs;

    private boolean isAudioMode = true;

    private SeekBar seekBar;

    private Handler seekBarHandler;



// ВИПРАВЛЕНО: Постійні права доступу

    private final ActivityResultLauncher<String[]> docPicker = registerForActivityResult(

            new ActivityResultContracts.OpenDocument(), uri -> {

                if (uri != null) {

                    try {

                        requireContext().getContentResolver().takePersistableUriPermission(uri,

                                Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    } catch (Exception e) { e.printStackTrace(); }

                    addMedia(uri);

                }

            });



    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(

            new ActivityResultContracts.RequestMultiplePermissions(), result -> {});



    @Nullable

    @Override

    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_second, container, false);

    }



    @Override

    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);



        prefs = requireActivity().getSharedPreferences("PlaylistPrefs", Context.MODE_PRIVATE);

        loadSavedMedia();

        checkAndRequestPermissions();



        player = new ExoPlayer.Builder(requireContext()).build();

        videoView = view.findViewById(R.id.videoPlayerView);

        videoView.setPlayer(player);



        ListView listView = view.findViewById(R.id.mediaListView);

        adapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, displayList) {

            @NonNull

            @Override

            public View getView(int pos, @Nullable View conv, @NonNull ViewGroup par) {

                View v = super.getView(pos, conv, par);

                TextView text = v.findViewById(android.R.id.text1);

                text.setTextColor(0xFFF2FF00);

                return v;

            }

        };

        listView.setAdapter(adapter);



// Реактивна зміна іконки Play/Pause

        player.addListener(new Player.Listener() {

            @Override

            public void onIsPlayingChanged(boolean isPlaying) {

                ImageButton btn = view.findViewById(R.id.btnPlayPause);

                if (btn != null) {

                    btn.setImageResource(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);

                }

            }

        });



        listView.setOnItemClickListener((p, v1, pos, id) -> {

            Uri uri = isAudioMode ? audioUris.get(pos) : videoUris.get(pos);

            startPlayback(uri, displayList.get(pos));

        });



        listView.setOnItemLongClickListener((p, v1, pos, id) -> {

            showDeleteDialog(pos);

            return true;

        });



// Меню та кнопки

        view.findViewById(R.id.menuAudio).setOnClickListener(v -> switchMode(true));

        view.findViewById(R.id.menuVideo).setOnClickListener(v -> switchMode(false));

        view.findViewById(R.id.menuAddFile).setOnClickListener(v -> {

            docPicker.launch(new String[]{"audio/*", "video/*"});

            closeDrawer();

        });

        view.findViewById(R.id.menuAddWeb).setOnClickListener(v -> showUrlDialog());

        view.findViewById(R.id.btnMenu).setOnClickListener(v ->

                ((DrawerLayout)view.findViewById(R.id.drawer_layout)).openDrawer(GravityCompat.START));



        view.findViewById(R.id.btnPlayPause).setOnClickListener(v -> {

            if (player.isPlaying()) player.pause(); else player.play();

        });



        view.findViewById(R.id.btnStop).setOnClickListener(v -> {

            player.stop();

            view.findViewById(R.id.miniPlayer).setVisibility(View.GONE);

            videoView.setVisibility(View.GONE);

        });



        seekBar = view.findViewById(R.id.playerSeekBar);

        setupSeekBar();

        switchMode(true);

    }



    private void checkAndRequestPermissions() {

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {

            permissionLauncher.launch(new String[]{

                    android.Manifest.permission.READ_MEDIA_AUDIO,

                    android.Manifest.permission.READ_MEDIA_VIDEO

            });

        } else {

            permissionLauncher.launch(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE});

        }

    }



    private void startPlayback(Uri uri, String name) {

        player.setMediaItem(MediaItem.fromUri(uri));

        player.prepare();

        player.play();

        View v = getView();

        if (v != null) {

            v.findViewById(R.id.miniPlayer).setVisibility(View.VISIBLE);

            v.findViewById(R.id.videoPlayerView).setVisibility(isAudioMode ? View.GONE : View.VISIBLE);

            ((TextView)v.findViewById(R.id.currentTrackName)).setText(name);

        }

    }



    private void addMedia(Uri uri) {

        String path = uri.toString().toLowerCase();



// Якщо в посиланні є розширення - ідентифікуємо автоматично

        if (path.endsWith(".mp4") || path.endsWith(".mkv") || path.contains("video")) {

            showFormatDialog(uri); // Запитуємо формат для відео

        } else if (path.endsWith(".mp3") || path.endsWith(".wav") || path.contains("audio")) {

            if (!audioUris.contains(uri)) {

                audioUris.add(uri);

                saveToPrefs("audio", audioUris);

            }

            switchMode(isAudioMode);

        } else {

            showFormatDialog(uri);

        }

    }



    private void showFormatDialog(Uri uri) {

        String[] options = {"As Video", "As Audio", "Both"};

        new AlertDialog.Builder(requireContext()).setTitle("Select format")

                .setItems(options, (d, w) -> {

                    if (w == 0 || w == 2) { videoUris.add(uri); saveToPrefs("video", videoUris); }

                    if (w == 1 || w == 2) { audioUris.add(uri); saveToPrefs("audio", audioUris); }

                    switchMode(isAudioMode);

                }).show();

    }



    private void showDeleteDialog(int position) {

        new AlertDialog.Builder(requireContext()).setTitle("Delete").setMessage("Remove item?")

                .setPositiveButton("Delete", (d, w) -> {

                    if (isAudioMode) audioUris.remove(position); else videoUris.remove(position);

                    saveToPrefs(isAudioMode ? "audio" : "video", isAudioMode ? audioUris : videoUris);

                    switchMode(isAudioMode);

                }).setNegativeButton("Cancel", null).show();

    }



    private void showUrlDialog() {

        closeDrawer();

        final EditText input = new EditText(requireContext());

        input.setHint("https://example.com/file.mp3");

        new AlertDialog.Builder(requireContext()).setTitle("Add from Link").setView(input)

                .setPositiveButton("Add", (d, w) -> {

                    String url = input.getText().toString().trim();

                    if (!url.isEmpty()) addMedia(Uri.parse(url));

                }).setNegativeButton("Cancel", null).show();

    }



    private void switchMode(boolean audio) {

        isAudioMode = audio;

        displayList.clear();

        List<Uri> target = audio ? audioUris : videoUris;

        for (Uri u : target) {

            String name = u.getLastPathSegment();

            displayList.add(name != null ? name : "Web stream");

        }



        View v = getView();

        if (v != null) {

            v.findViewById(R.id.menuAudio).setBackgroundTintList(android.content.res.ColorStateList.valueOf(audio ? 0xFFF2FF00 : 0xFF333333));

            v.findViewById(R.id.menuVideo).setBackgroundTintList(android.content.res.ColorStateList.valueOf(!audio ? 0xFFF2FF00 : 0xFF333333));

            ((TextView)v.findViewById(R.id.mainTitle)).setText(audio ? "MUSIC LIBRARY" : "VIDEO LIBRARY");

        }

        adapter.notifyDataSetChanged();

        closeDrawer();

    }



    private void saveToPrefs(String key, List<Uri> uris) {

        Set<String> set = new HashSet<>();

        for (Uri u : uris) set.add(u.toString());

        prefs.edit().putStringSet(key, set).apply();

    }



    private void loadSavedMedia() {

        audioUris.clear(); videoUris.clear();

        Set<String> audios = prefs.getStringSet("audio", new HashSet<>());

        Set<String> videos = prefs.getStringSet("video", new HashSet<>());

        for (String s : audios) audioUris.add(Uri.parse(s));

        for (String s : videos) videoUris.add(Uri.parse(s));

    }



    private void setupSeekBar() {

        seekBarHandler = new Handler(Looper.getMainLooper());

        requireActivity().runOnUiThread(new Runnable() {

            @Override public void run() {

                if (player != null && player.isPlaying()) {

                    seekBar.setMax((int) player.getDuration());

                    seekBar.setProgress((int) player.getCurrentPosition());

                }

                seekBarHandler.postDelayed(this, 1000);

            }

        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override public void onProgressChanged(SeekBar sb, int p, boolean f) { if (f) player.seekTo(p); }

            @Override public void onStartTrackingTouch(SeekBar sb) {}

            @Override public void onStopTrackingTouch(SeekBar sb) {}

        });

    }



    private void closeDrawer() { if (getView() != null) ((DrawerLayout)getView().findViewById(R.id.drawer_layout)).closeDrawers(); }



    @Override

    public void onDestroy() { super.onDestroy(); if (player != null) player.release(); }

}