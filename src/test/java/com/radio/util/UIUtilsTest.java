package com.radio.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UIUtilsTest {

    @Test
    void playbackAnimationFrame_isStableForSameFrameIndex() {
        assertThat(UIUtils.playbackAnimationFrame(0)).isEqualTo(UIUtils.playbackAnimationFrame(0));
    }

    @Test
    void playbackAnimationFrame_wrapsNegativeAndLargeIndexes() {
        assertThat(UIUtils.playbackAnimationFrame(-1)).isEqualTo(UIUtils.playbackAnimationFrame(3));
        assertThat(UIUtils.playbackAnimationFrame(4)).isEqualTo(UIUtils.playbackAnimationFrame(0));
    }

    @Test
    void playbackPromptFrame_wrapsNegativeAndLargeIndexes() {
        assertThat(UIUtils.playbackPromptFrame(-1)).isEqualTo(UIUtils.playbackPromptFrame(3));
        assertThat(UIUtils.playbackPromptFrame(4)).isEqualTo(UIUtils.playbackPromptFrame(0));
    }
}
