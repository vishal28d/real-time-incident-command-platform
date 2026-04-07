export const metadata = {
  title: "Incident Command Center",
  description: "Real-time incident operations dashboard"
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body style={{ margin: 0, fontFamily: "Inter, Arial, sans-serif", background: "#0b1020", color: "#f4f7ff" }}>
        {children}
      </body>
    </html>
  );
}
