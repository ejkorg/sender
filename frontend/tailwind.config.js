/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts,scss}'],
  safelist: ['border-red-500', 'focus:ring-red-200'],
  theme: {
    extend: {
      colors: {
        onsemi: {
          // Andromeda-inspired blues for site theme
          primary: '#0B67FF',
          dark: '#0955CC',
          light: '#6FB0FF',
          accent: '#003A70',
          charcoal: '#0F1724',
          ice: '#F4FBFF'
        }
      },
      fontFamily: {
        sans: ['Inter', 'Roboto', 'Helvetica Neue', 'Arial', 'sans-serif']
      }
    }
  },
  plugins: []
};

