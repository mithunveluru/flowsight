import { Sidebar } from "./sidebar";
import { Header } from "./header";

interface ShellProps {
  children: React.ReactNode;
  title?: string;
  description?: string;
  actions?: React.ReactNode;
}

export function Shell({ children, title, description, actions }: ShellProps) {
  return (
    <div className="flex h-screen overflow-hidden bg-background">
      <Sidebar />

      <div className="flex flex-1 flex-col overflow-hidden">
        <Header title={title} description={description} actions={actions} />
        <main className="flex-1 overflow-y-auto">
          {/* Generous padding + max-width for premium readability */}
          <div className="mx-auto w-full max-w-6xl px-8 py-10 lg:px-12 lg:py-12">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
