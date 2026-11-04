import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

/*
 * Same tokens as the Android app (apps/android/.../presentation/theme): one blue accent,
 * neutral surfaces, red only for destructive actions. Keep both in sync; the table lives
 * in CLAUDE.md. Values follow the iOS system palette.
 */
const accent = {
  50: '#EAF3FF',
  100: '#D6E8FF',
  200: '#ADD1FF',
  300: '#7DB6FF',
  400: '#4F9BFF',
  500: '#1F86FF',
  600: '#007AFF',
  700: '#0062CC',
  800: '#004A99',
  900: '#003166',
  950: '#001E40',
};

const red = {
  50: '#FFF1F0',
  100: '#FFE0DE',
  200: '#FFC2BE',
  300: '#FF9A93',
  400: '#FF6B61',
  500: '#FF3B30',
  600: '#E0291F',
  700: '#B81F17',
  800: '#8F1913',
  900: '#6B130F',
  950: '#430B09',
};

export const portalPreset = definePreset(Aura, {
  primitive: {
    blue: accent,
    red,
  },
  semantic: {
    transitionDuration: '200ms',
    focusRing: {
      width: '3px',
      style: 'solid',
      color: 'light-dark(rgba(0, 122, 255, 0.35), rgba(10, 132, 255, 0.5))',
      offset: '0',
      shadow: 'none',
    },
    primary: {
      ...accent,
      color: 'light-dark(#007AFF, #0A84FF)',
      contrastColor: '#ffffff',
      hoverColor: 'light-dark(#0062CC, #409CFF)',
      activeColor: 'light-dark(#004A99, #64B0FF)',
    },
    // Aura paints dark overlays with the high end of this scale (surface.800–950).
    // Keep 0 light and 950 dark in both schemes; do not invert it with light-dark().
    surface: {
      0: '#FFFFFF',
      50: '#F9F9FB',
      100: '#F2F2F7',
      200: '#E9E9EE',
      300: '#D9D9DE',
      400: '#B9B9C0',
      500: '#8E8E93',
      600: '#6C6C70',
      700: '#48484A',
      800: '#2C2C2E',
      900: '#1C1C1E',
      950: '#000000',
    },
    content: {
      borderRadius: '14px',
      background: 'light-dark(#FFFFFF, #1C1C1E)',
      hoverBackground: 'light-dark(#F2F2F7, #2C2C2E)',
      borderColor: 'light-dark(#D9D9DE, #38383A)',
    },
    text: {
      color: 'light-dark(#000000, #FFFFFF)',
      hoverColor: 'light-dark(#000000, #FFFFFF)',
      mutedColor: 'light-dark(#6C6C70, #AEAEB2)',
      hoverMutedColor: 'light-dark(#48484A, #D1D1D6)',
    },
    // Filled, borderless fields: the fill is the affordance, the ring appears on focus.
    formField: {
      borderRadius: '10px',
      paddingX: '12px',
      paddingY: '9px',
      background: 'light-dark(#EBEBF0, #2C2C2E)',
      disabledBackground: 'light-dark(#E9E9EE, #1C1C1E)',
      filledBackground: 'light-dark(#EBEBF0, #2C2C2E)',
      filledHoverBackground: 'light-dark(#E3E3E8, #3A3A3C)',
      filledFocusBackground: 'light-dark(#EBEBF0, #2C2C2E)',
      borderColor: 'transparent',
      hoverBorderColor: 'transparent',
      focusBorderColor: 'light-dark(#007AFF, #0A84FF)',
      color: 'light-dark(#000000, #FFFFFF)',
      placeholderColor: 'light-dark(#8E8E93, #8E8E93)',
      focusRing: {
        width: '3px',
        style: 'solid',
        color: 'light-dark(rgba(0, 122, 255, 0.28), rgba(10, 132, 255, 0.4))',
        offset: '0',
        shadow: 'none',
      },
    },
    typography: {
      fontFamily:
        '-apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Display", Inter, "Segoe UI", system-ui, sans-serif',
      fontSize: '0.9375rem',
      lineHeight: '1.45',
    },
  },
  components: {
    button: {
      root: {
        borderRadius: '10px',
        paddingX: '16px',
        paddingY: '8px',
        gap: '8px',
        label: { fontWeight: '600' },
        transitionDuration: '160ms',
      },
    },
    card: {
      root: {
        borderRadius: '14px',
        shadow: 'none',
      },
      body: { padding: '16px', gap: '12px' },
      title: { fontSize: '1.0625rem', fontWeight: '600' },
      subtitle: { color: '{text.muted.color}' },
    },
    dialog: {
      root: { borderRadius: '14px' },
    },
    tag: {
      root: { fontWeight: '600' },
    },
    toast: {
      root: { borderRadius: '14px' },
    },
  },
});
