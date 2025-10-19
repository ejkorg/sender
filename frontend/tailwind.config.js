/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts,scss}'],
  safelist: ['border-red-500', 'focus:ring-red-200'],
  theme: {
    extend: {
      colors: {
        onsemi: {
          primary: '#00953B',
          dark: '#00682A',
          light: '#4DC471',
          accent: '#00A3AD',
          charcoal: '#101820',
          ice: '#F4FAF7'
        }
      },
      fontFamily: {
        sans: ['Inter', 'Roboto', 'Helvetica Neue', 'Arial', 'sans-serif']
      }
    }
  },
  plugins: []
};

