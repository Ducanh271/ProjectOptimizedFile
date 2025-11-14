package com.example.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import java.text.DateFormat;
import java.util.Date;

public class Widget extends AppWidgetProvider {

    private static final String SHARED_PREF_FILE = "com.example.mywidgetapp.prefs";
    private static final String COUNT_KEY = "count_";

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        // Lấy count từ SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_FILE, Context.MODE_PRIVATE);
        int count = prefs.getInt(COUNT_KEY + appWidgetId, 0);
        count++;

        // Lấy thời gian hiện tại
        String dateString = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());

        // Tạo RemoteViews để cập nhật giao diện widget
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.my_app_widget);
        views.setTextViewText(R.id.appwidget_id, "Widget ID: " + appWidgetId);
        views.setTextViewText(R.id.appwidget_update,
                context.getString(R.string.date_count_format, count, dateString));

        // Lưu count mới vào SharedPreferences
        SharedPreferences.Editor prefEditor = prefs.edit();
        prefEditor.putInt(COUNT_KEY + appWidgetId, count);
        prefEditor.apply();

        // Tạo Intent để xử lý sự kiện nhấn nút
        Intent intentUpdate = new Intent(context, Widget.class);
        intentUpdate.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        int[] idArray = new int[]{appWidgetId};
        intentUpdate.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, idArray);

        // Tạo PendingIntent cho nút
        PendingIntent pendingUpdate = PendingIntent.getBroadcast(
                context, appWidgetId, intentUpdate, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.button_update, pendingUpdate);

        // Cập nhật widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // Cập nhật tất cả các instance của widget
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onEnabled(Context context) {
        // Gọi khi widget đầu tiên được tạo
    }

    @Override
    public void onDisabled(Context context) {
        // Gọi khi widget cuối cùng bị xóa
    }
}