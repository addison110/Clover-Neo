/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.ui.controller;

import android.content.Context;

import org.floens.chan.R;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.settings.BooleanSettingView;
import org.floens.chan.ui.settings.ListSettingView;
import org.floens.chan.ui.settings.SettingView;
import org.floens.chan.ui.settings.SettingsController;
import org.floens.chan.ui.settings.SettingsGroup;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.getString;

public class VideoSettingsController extends SettingsController {
    private SettingView builtInPlayer;
    private final List<SettingView> controlsSettings = new ArrayList<>();

    public VideoSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.settings_screen_video);

        setupLayout();

        populatePreferences();

        buildPreferences();

        updateControlsVisibility(false);
    }

    @Override
    public void onPreferenceChange(SettingView item) {
        super.onPreferenceChange(item);

        if (item == builtInPlayer) {
            updateControlsVisibility(true);
        }
    }

    // The controls only belong to the built-in player
    private void updateControlsVisibility(boolean animated) {
        boolean visible = ChanSettings.videoUseExoplayer.get();
        for (SettingView setting : controlsSettings) {
            setSettingViewVisibility(setting, visible, animated);
        }
    }

    private void populatePreferences() {
        // Player group
        {
            SettingsGroup player = new SettingsGroup(R.string.settings_group_video_player);

            builtInPlayer = player.add(new BooleanSettingView(this,
                    ChanSettings.videoUseExoplayer,
                    R.string.setting_video_exoplayer,
                    R.string.setting_video_exoplayer_description));

            player.add(new BooleanSettingView(this,
                    ChanSettings.videoOpenExternal,
                    R.string.setting_video_open_external,
                    R.string.setting_video_open_external_description));

            player.add(new BooleanSettingView(this,
                    ChanSettings.videoDefaultMuted,
                    R.string.setting_video_default_muted,
                    R.string.setting_video_default_muted_description));

            player.add(new BooleanSettingView(this,
                    ChanSettings.videoAutoLoop,
                    R.string.setting_video_auto_loop,
                    R.string.setting_video_auto_loop_description));

            groups.add(player);
        }

        // Controls group
        {
            SettingsGroup controls = new SettingsGroup(R.string.settings_group_video_controls);

            List<ListSettingView.Item> positions = new ArrayList<>();
            positions.add(new ListSettingView.Item<>(
                    getString(R.string.setting_video_controls_position_bottom), "bottom"));
            positions.add(new ListSettingView.Item<>(
                    getString(R.string.setting_video_controls_position_center), "center"));
            controlsSettings.add(controls.add(new ListSettingView<>(this,
                    ChanSettings.videoControlsPosition,
                    R.string.setting_video_controls_position, positions)));

            controlsSettings.add(controls.add(new BooleanSettingView(this,
                    ChanSettings.videoControlsAutoShow,
                    R.string.setting_video_controls_auto_show,
                    R.string.setting_video_controls_auto_show_description)));

            List<ListSettingView.Item> timeouts = secondsItems(2, 3, 5, 10);
            // Media3 keeps the controls visible without a timeout
            timeouts.add(new ListSettingView.Item<>(
                    getString(R.string.setting_video_controls_timeout_never), 0));
            controlsSettings.add(controls.add(new ListSettingView<>(this,
                    ChanSettings.videoControlsTimeout,
                    R.string.setting_video_controls_timeout, timeouts)));

            controlsSettings.add(controls.add(new BooleanSettingView(this,
                    ChanSettings.videoSeekButtons,
                    R.string.setting_video_seek_buttons,
                    R.string.setting_video_seek_buttons_description)));

            controlsSettings.add(controls.add(new BooleanSettingView(this,
                    ChanSettings.videoDoubleTapSeek,
                    R.string.setting_video_double_tap_seek,
                    R.string.setting_video_double_tap_seek_description)));

            controlsSettings.add(controls.add(new ListSettingView<>(this,
                    ChanSettings.videoSeekIncrement,
                    R.string.setting_video_seek_increment,
                    secondsItems(5, 10, 15, 30))));

            groups.add(controls);
        }
    }

    private List<ListSettingView.Item> secondsItems(int... seconds) {
        List<ListSettingView.Item> items = new ArrayList<>();
        for (int second : seconds) {
            items.add(new ListSettingView.Item<>(context.getResources().getQuantityString(
                    R.plurals.setting_video_seconds, second, second), second * 1000));
        }
        return items;
    }
}
