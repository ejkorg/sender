/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts,scss}'],
  safelist: ['border-red-500', 'focus:ring-red-200'],
  theme: {
    extend: {
      colors: {
        onsemi: {
          // onsemi branding — primary switched to onsemi orange
          primary: '#DA7F3A',
          dark: '#b0682f',
          light: '#eaa87a',
          accent: '#101820',
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

