import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

const sage = {
  50: '#F4F7F5',
  100: '#E6EFE9',
  200: '#CDDFD4',
  300: '#A9C6B4',
  400: '#8AAD98',
  500: '#6E927E',
  600: '#587864',
  700: '#466353',
  800: '#3A5145',
  900: '#314339',
  950: '#1B2822',
};

const rose = {
  50: '#FBF6F5',
  100: '#F6E8E5',
  200: '#EDD0CB',
  300: '#DEB0A8',
  400: '#C98E84',
  500: '#9A5E55',
  600: '#7E4C45',
  700: '#68413C',
  800: '#573834',
  900: '#3E2825',
  950: '#2E1C19',
};

const sand = {
  50: '#FBF7F1',
  100: '#F4EADF',
  200: '#E8D4BE',
  300: '#D6B892',
  400: '#C4A06E',
  500: '#B08A56',
  600: '#947246',
  700: '#785C39',
  800: '#624C32',
  900: '#52402C',
  950: '#2C2116',
};

export const portalPreset = definePreset(Aura, {
  primitive: {
    green: {
      50: '#F3F7F4',
      100: '#E3EEE6',
      200: '#C5DCCE',
      300: '#9FC2AD',
      400: '#7AA892',
      500: '#5E8C76',
      600: '#4C7360',
      700: '#3E5D4E',
      800: '#334C41',
      900: '#2B3F36',
      950: '#17241E',
    },
    red: rose,
    orange: sand,
    amber: sand,
  },
  semantic: {
    transitionDuration: '200ms',
    focusRing: {
      width: '2px',
      style: 'solid',
      color: '{primary.color}',
      offset: '2px',
      shadow: 'none',
    },
    primary: {
      ...sage,
      color: 'light-dark({primary.600}, {primary.300})',
      contrastColor: 'light-dark(#ffffff, {primary.950})',
      hoverColor: 'light-dark({primary.700}, {primary.200})',
      activeColor: 'light-dark({primary.800}, {primary.100})',
    },
    surface: {
      0: '#FFFCF9',
      50: 'light-dark(#F7F4EF, #2A2826)',
      100: 'light-dark(#F1EDE6, #332F2C)',
      200: 'light-dark(#E5DFD6, #3F3A36)',
      300: 'light-dark(#D3CBC0, #524C46)',
      400: 'light-dark(#B6ADA2, #7A726A)',
      500: 'light-dark(#8C837A, #A39A90)',
      600: 'light-dark(#6E675F, #C4BBB2)',
      700: 'light-dark(#4E4944, #DDD6CE)',
      800: 'light-dark(#38342F, #EBE6DF)',
      900: 'light-dark(#2C2926, #F6F3EE)',
      950: 'light-dark(#1A1816, #FBF9F6)',
    },
    content: {
      borderRadius: '12px',
      background: 'light-dark(#FBF9F6, #242220)',
      hoverBackground: 'light-dark(#F3EFEA, #2E2B28)',
      borderColor: 'light-dark(#E4DDD4, #3C3834)',
    },
    text: {
      color: 'light-dark(#2C2926, #F4F1EC)',
      hoverColor: 'light-dark(#1A1816, #FFFFFF)',
      mutedColor: 'light-dark(#6E675F, #B7AEA4)',
      hoverMutedColor: 'light-dark(#4E4944, #DDD6CE)',
    },
    formField: {
      borderRadius: '8px',
      paddingX: '12px',
      paddingY: '8px',
      background: 'light-dark(#FFFCF9, #1E1C1A)',
      borderColor: 'light-dark(#E4DDD4, #3C3834)',
      hoverBorderColor: 'light-dark(#C9C0B6, #5C564F)',
      color: 'light-dark(#2C2926, #F4F1EC)',
      placeholderColor: 'light-dark(#8C837A, #8C837A)',
      focusRing: {
        width: '2px',
        style: 'solid',
        color: '{primary.color}',
        offset: '2px',
        shadow: 'none',
      },
    },
    typography: {
      fontFamily: 'Inter, "Segoe UI", system-ui, sans-serif',
      fontSize: '0.9375rem',
      lineHeight: '1.5',
    },
  },
  components: {
    button: {
      root: {
        borderRadius: '8px',
        paddingX: '12px',
        paddingY: '8px',
        gap: '8px',
        label: { fontWeight: '500' },
        transitionDuration: '200ms',
      },
    },
    card: {
      root: {
        borderRadius: '12px',
        shadow: '0 1px 2px rgba(44, 41, 38, 0.05)',
      },
      body: { padding: '16px', gap: '12px' },
      title: { fontSize: '1.125rem', fontWeight: '600' },
      subtitle: { color: '{text.muted.color}' },
    },
    dialog: {
      root: { borderRadius: '12px' },
    },
    tag: {
      root: { fontWeight: '500' },
    },
    toast: {
      root: { borderRadius: '12px' },
    },
  },
});
