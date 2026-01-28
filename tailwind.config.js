/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./app/**/*.{js,jsx,ts,tsx}", "./components/**/*.{js,jsx,ts,tsx}"],
  presets: [require("nativewind/preset")],
  theme: {
    extend: {
      colors: {
        "primary": "#993df5",
        "background-light": "#f7f5f8",
        "background-dark": "#191022",
        "surface-dark": "#2a1f36",
      },
    },
  },
  plugins: [],
}
