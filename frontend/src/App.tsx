import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'

type Message = { role: 'user' | 'assistant'; text: string }
type ChatResponse = { conversationId: string; reply: string }

const SUGGESTIONS = [
  'Plan a 5-day trip to Japan in spring',
  'Cheap beach destinations in Asia for December',
  'What should I pack for a trek in the Himalayas?',
]

function App() {
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const conversationId = useRef<string | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

  async function send(text: string) {
    const message = text.trim()
    if (!message || loading) return
    setMessages((prev) => [...prev, { role: 'user', text: message }])
    setInput('')
    setError(null)
    setLoading(true)
    try {
      const res = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message, conversationId: conversationId.current }),
      })
      if (!res.ok) {
        const problem = await res.json().catch(() => null)
        throw new Error(problem?.detail ?? `Request failed (HTTP ${res.status})`)
      }
      const data = (await res.json()) as ChatResponse
      conversationId.current = data.conversationId
      setMessages((prev) => [...prev, { role: 'assistant', text: data.reply }])
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    void send(input)
  }

  function onKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      void send(input)
    }
  }

  function newChat() {
    conversationId.current = null
    setMessages([])
    setError(null)
  }

  return (
    <main className="app">
      <header className="header">
        <h1>Travel Agent</h1>
        {messages.length > 0 && (
          <button type="button" className="link" onClick={newChat} disabled={loading}>
            New chat
          </button>
        )}
      </header>

      <section className="messages">
        {messages.length === 0 && (
          <div className="empty">
            <p>Ask me anything about planning your next trip.</p>
            <div className="suggestions">
              {SUGGESTIONS.map((s) => (
                <button key={s} type="button" onClick={() => void send(s)}>
                  {s}
                </button>
              ))}
            </div>
          </div>
        )}
        {messages.map((m, i) => (
          <div key={i} className={`bubble ${m.role}`}>
            {m.text}
          </div>
        ))}
        {loading && <div className="bubble assistant pending">Thinking…</div>}
        {error && <div className="error">{error}</div>}
        <div ref={bottomRef} />
      </section>

      <form className="composer" onSubmit={onSubmit}>
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder="Where do you want to go?"
          rows={2}
          maxLength={4000}
          aria-label="Your question"
        />
        <button type="submit" disabled={loading || !input.trim()}>
          Send
        </button>
      </form>
    </main>
  )
}

export default App
