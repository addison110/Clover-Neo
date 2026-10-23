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
import android.view.View;

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
    private SettingView controlsPosition;
    private SettingView autoShow;
    private SettingView alwaysShow;
    private SettingView timeout;
    private SettingView seekButtons;
    private SettingView doubleTapSeek;
    private SettingView seekIncrement;

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

        updateControlsVisibility();
        updateAlwaysShowEnabled();
        updateSeekIncrementEnabled();
    }

    @Override
    public void onPreferenceChange(SettingView item) {
        super.onPreferenceChange(item);

        if (item == builtInPlayer) {
            updateControlsVisibility();
        } else if (item == alwaysShow) {
            updateAlwaysShowEnabled();
        } else if (item == seekButtons || item == doubleTapSeek) {
            updateSeekIncrementEnabled();
        }
    }

    // The controls only belong to the built-in player, hide their whole group, header included
    private void updateControlsVisibility() {
        View group = (View) controlsPosition.view.getParent();
        group.setVisibility(ChanSettings.videoUseExoplayer.get() ? View.VISIBLE : View.GONE);
    }

    // Always shown controls make showing them at the start and hiding them after a while moot
    private void updateAlwaysShowEnabled() {
        boolean always = ChanSettings.videoControlsAlwaysShow.get();
        autoShow.setEnabled(!always);
        timeout.setEnabled(!always);
    }

    // Only the skip buttons and double-tapping skip
    private void updateSeekIncrementEnabled() {
        seekIncrement.setEnabled(
                ChanSettings.videoSeekButtons.get() || ChanSettings.videoDoubleTapSeek.get());
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
            controlsPosition = controls.add(new ListSettingView<>(this,
                    ChanSettings.videoControlsPosition,
                    R.string.setting_video_controls_position, positions));

            alwaysShow = controls.add(new BooleanSettingView(this,
                    ChanSettings.videoControlsAlwaysShow,
                    R.string.setting_video_controls_always_show,
                    R.string.setting_video_controls_always_show_description));

            autoShow = controls.add(new BooleanSettingView(this,
                    ChanSettings.videoControlsAutoShow,
                    R.string.setting_video_controls_auto_show,
                    R.string.setting_video_controls_auto_show_description));

            List<ListSettingView.Item> timeouts = secondsItems(2, 3, 5, 10);
            // Media3 keeps the controls visible without a timeout
            timeouts.add(new ListSettingView.Item<>(
                    getString(R.string.setting_video_controls_timeout_never), 0));
            timeout = controls.add(new ListSettingView<>(this,
                    ChanSettings.videoControlsTimeout,
                    R.string.setting_video_controls_timeout, timeouts));

            seekButtons = controls.add(new BooleanSettingView(this,
                    ChanSettings.videoSeekButtons,
                    R.string.setting_video_seek_buttons,
                    R.string.setting_video_seek_buttons_description));

            doubleTapSeek = controls.add(new BooleanSettingView(this,
                    ChanSettings.videoDoubleTapSeek,
                    R.string.setting_video_double_tap_seek,
                    R.string.setting_video_double_tap_seek_description));

            seekIncrement = controls.add(new ListSettingView<>(this,
                    ChanSettings.videoSeekIncrement,
                    R.string.setting_video_seek_increment,
                    secondsItems(5, 10, 15, 30)));

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
