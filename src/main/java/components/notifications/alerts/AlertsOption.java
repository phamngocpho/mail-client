package components.notifications.alerts;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import components.notifications.swing.AnimateIcon;
import components.notifications.swing.animator.EasingInterpolator;
import components.notifications.swing.animator.KeyFrames;

import javax.swing.*;
import java.awt.*;

public class AlertsOption {

    protected Icon icon;
    protected Color baseColor;

    protected boolean loopAnimation;

    protected EffectOption effectOption;

    public AlertsOption(Icon icon, Color baseColor) {
        this.icon = icon;
        this.baseColor = baseColor;
    }

    public AlertsOption setEffectOption(EffectOption effectOption) {
        this.effectOption = effectOption;
        return this;
    }

    public AlertsOption setLoopAnimation(boolean loopAnimation) {
        this.loopAnimation = loopAnimation;
        return this;
    }

    public static class EffectOption {

        protected float effectAlpha = 1f;
        protected boolean effectFadeOut = false;
        protected Icon[] randomEffect;

        public EffectOption setEffectAlpha(float effectAlpha) {
            this.effectAlpha = effectAlpha;
            return this;
        }

        public EffectOption setEffectFadeOut(boolean effectFadeOut) {
            this.effectFadeOut = effectFadeOut;
            return this;
        }

        public EffectOption setRandomEffect(Icon[] randomEffect) {
            this.randomEffect = randomEffect;
            return this;
        }
    }


    protected static AlertsOption getAlertsOption(MessageAlerts.MessageType messageType) {
        if (messageType == MessageAlerts.MessageType.SUCCESS) {
            Icon effects[] = new Icon[]{
                    new FlatSVGIcon("Pictures/effect/check.svg"),
                    new FlatSVGIcon("Pictures/effect/starred.svg"),
                    new FlatSVGIcon("Pictures/effect/firework.svg"),
                    new FlatSVGIcon("Pictures/effect/balloon.svg")
            };
            return getDefaultOption("Pictures/icon/success.svg", Color.decode("#10b981"), effects);
        } else if (messageType == MessageAlerts.MessageType.WARNING) {
            Icon effects[] = new Icon[]{
                    new FlatSVGIcon("Pictures/effect/disclaimer.svg"),
                    new FlatSVGIcon("Pictures/effect/warning.svg"),
                    new FlatSVGIcon("Pictures/effect/query.svg"),
                    new FlatSVGIcon("Pictures/effect/mark.svg")
            };
            return getDefaultOption("Pictures/icon/warning.svg", Color.decode("#f59e0b"), effects);
        } else if (messageType == MessageAlerts.MessageType.ERROR) {
            Icon effects[] = new Icon[]{
                    new FlatSVGIcon("Pictures/effect/error.svg"),
                    new FlatSVGIcon("Pictures/effect/sad.svg"),
                    new FlatSVGIcon("Pictures/effect/shield.svg"),
                    new FlatSVGIcon("Pictures/effect/nothing.svg")
            };
            return getDefaultOption("Pictures/Icon/Pics1/error.svg", Color.decode("#ef4444"), effects);
        } else {
            return getDefaultOption("Pictures/Icon/Pics1/information.svg", null);
        }
    }

    private static AlertsOption getDefaultOption(String icon, Color color, Icon[] effects) {
        AnimateIcon.AnimateOption option = new AnimateIcon.AnimateOption()
                .setInterpolator(EasingInterpolator.EASE_OUT_BOUNCE)
                .setScaleInterpolator(new KeyFrames(1f, 1.5f, 1f))
                .setRotateInterpolator(new KeyFrames(0f, (float) Math.toRadians(-30f), 0f));
        return new AlertsOption(new AnimateIcon(icon, 4f, option), color)
                .setEffectOption(new EffectOption()
                        .setEffectAlpha(0.9f)
                        .setEffectFadeOut(true)
                        .setRandomEffect(effects))
                .setLoopAnimation(true);
    }

    public static AlertsOption getDefaultOption(String icon, Color color) {
        AnimateIcon.AnimateOption option = new AnimateIcon.AnimateOption()
                .setScaleInterpolator(new KeyFrames(1f, 1.2f, 1f))
                .setRotateInterpolator(new KeyFrames(0f, (float) Math.toRadians(-30), (float) Math.toRadians(30), 0f));
        return new AlertsOption(new AnimateIcon(icon, 4f, option), color)
                .setLoopAnimation(true);
    }
}
