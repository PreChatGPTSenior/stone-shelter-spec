import { useEffect, useId, useRef } from 'react';
import { stoneDetailsPath } from '../../../../App/navigation/pageRoutes';
import { useChatSession } from '../../state/ChatSessionProvider/ChatSessionProvider';
import { ChatStreamPhase } from '../../../../enums/ChatStreamPhase';
import { ChatMessageDeliveryState } from '../../../../enums/ChatMessageDeliveryState';
import './StoneChatbot.css';

const quickPrompts = ['Help me choose a stone', 'Tell me about stone properties', 'How do I care for a stone?', 'Another topic'];

export function StoneChatbot({ visible }: { visible: boolean }) {
  const chatSession = useChatSession();
  const panelId = useId();
  const widget = useRef<HTMLDivElement>(null);
  const launcher = useRef<HTMLButtonElement>(null);
  const input = useRef<HTMLInputElement>(null);
  const history = useRef<HTMLDivElement>(null);
  const { open, setOpen, messages, pending, error } = chatSession;
  const followBottom = useRef(true);
  const blocked = pending || Boolean(error) || chatSession.restoring || chatSession.unavailable || chatSession.cooldown > 0;
  const inputError = chatSession.inputError ?? (chatSession.draft.length > 2000 ? 'Use at most 2000 characters.' : undefined);

  useEffect(() => {
    if (!open) return;
    input.current?.focus({ preventScroll: true });
    function keydown(event: KeyboardEvent) {
      if (event.key !== 'Escape' || event.defaultPrevented || widget.current?.hidden
        || document.querySelector('[role="dialog"][aria-modal="true"]')) return;
      event.preventDefault();
      setOpen(false);
      launcher.current?.focus({ preventScroll: true });
    }
    document.addEventListener('keydown', keydown);
    return () => document.removeEventListener('keydown', keydown);
  }, [open, setOpen]);

  useEffect(() => {
    if (open && history.current && followBottom.current) history.current.scrollTop = history.current.scrollHeight;
  }, [open, messages, pending, error, visible]);

  function close() {
    setOpen(false);
    launcher.current?.focus({ preventScroll: true });
  }

  return (
    <div className="stone-chatbot" ref={widget} hidden={!visible} inert={!visible}>
      <section id={panelId} className="chat-panel" aria-labelledby={`${panelId}-title`} hidden={!open} inert={!open}>
        <header className="chat-heading">
          <span className="chat-avatar" aria-hidden="true">❧</span>
          <div><h2 id={`${panelId}-title`}>Stone Shelter Chat</h2>{chatSession.isMock && <span className="chat-demo">Mock assistant</span>}</div>
          <button className="chat-close" type="button" aria-label="Close chat" onClick={close}>×</button>
        </header>
        <div className="chat-history" role="log" aria-label="Chat messages" aria-live="off" tabIndex={0} ref={history} onScroll={() => { if (history.current) followBottom.current = history.current.scrollHeight - history.current.scrollTop - history.current.clientHeight <= 80; }}>
          {messages.length === 0 && !chatSession.restoring && !chatSession.unavailable && <p className="chat-bubble chat-assistant">Hello! I can help you explore our stones. What would you like to know?</p>}
          {messages.map(chatMessage => (
            <div key={`${chatMessage.turnId}-${chatMessage.role}`} className={`chat-bubble ${chatMessage.role === 'USER' ? 'chat-user' : 'chat-assistant'}`}>
              <span className="chat-speaker">{chatMessage.role === 'USER' ? 'You' : 'Stone Shelter'}</span>
              <p>{chatMessage.text}</p>
              {chatMessage.delivery === ChatMessageDeliveryState.Incomplete && <small className="chat-incomplete">Incomplete reply. Restore history to check the saved result.</small>}
              {chatMessage.delivery === ChatMessageDeliveryState.Confirmed && chatMessage.stones.length > 0 && <ul className="chat-stones">{chatMessage.stones.map((stoneChatStone, index) =>
                <li key={`${stoneChatStone.id}-${index}`}><a href={stoneDetailsPath(stoneChatStone.id)}>{stoneChatStone.name}<span aria-hidden="true"> ↗</span></a></li>)}</ul>}
            </div>
          ))}
          {messages.length === 0 && !chatSession.restoring && !chatSession.unavailable && <div className="chat-prompts" aria-label="Suggested questions">{quickPrompts.map(prompt =>
            <button type="button" key={prompt} disabled={blocked} onClick={() => chatSession.send(prompt)}>{prompt}</button>)}</div>}
          {chatSession.phase === ChatStreamPhase.Waiting && <p className="chat-waiting" aria-hidden="true">Waiting for a reply…</p>}
          {error && <div className="chat-error"><p role="alert">{error}</p>
            {chatSession.canRetry && <button type="button" disabled={pending || chatSession.restoring || chatSession.unavailable || chatSession.cooldown > 0} onClick={chatSession.retry}>Retry message</button>}</div>}
          {chatSession.phase === ChatStreamPhase.Synchronizing && <p className="chat-waiting">Synchronizing saved history…</p>}
          {chatSession.restoring && <p role="status">Restoring conversation…</p>}
          {chatSession.cooldown > 0 && <p role="status">Try again in {chatSession.cooldown} seconds.</p>}
          {chatSession.restoreCooldown > 0 && <p role="status">Restore history in {chatSession.restoreCooldown} seconds.</p>}
          <div className="chat-recovery">
            {(chatSession.unavailable || chatSession.restoreError) &&
            <p role="alert">{chatSession.restoreError ?? 'Delivery could not be confirmed. Restore history before retrying.'}</p>}
            <button type="button" disabled={pending || chatSession.restoring || chatSession.restoreCooldown > 0} onClick={() => void chatSession.restore()}>{chatSession.unavailable || chatSession.restoreError ? 'Restore history' : 'Refresh history'}</button>
          </div>
        </div>
        <form className="chat-composer" onSubmit={event => { event.preventDefault(); chatSession.send(); }}>
          <label htmlFor={`${panelId}-message`} className="chat-sr-only">Message</label>
          <div className="chat-input-row">
            <input id={`${panelId}-message`} ref={input} value={chatSession.draft} placeholder="Write a message…"
              maxLength={2000} aria-describedby={`${panelId}-limit${inputError ? ` ${panelId}-error` : ''}`}
              aria-invalid={Boolean(inputError)} onChange={event => chatSession.setDraft(event.target.value)}
              onKeyDown={event => {
                if (event.key !== 'Enter') return;
                event.preventDefault();
                if (!event.nativeEvent.isComposing && event.keyCode !== 229) chatSession.send();
              }} />
            <button className="chat-send" type="submit" aria-label="Send message"
              disabled={blocked || !chatSession.draft.trim() || chatSession.draft.length > 2000}>
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m4 4 17 8-17 8 3-8-3-8Zm3 8h14" /></svg>
            </button>
          </div>
          <p id={`${panelId}-limit`} className="chat-limit">{chatSession.isMock ? 'Mock replies · ' : ''}Up to 2000 characters</p>
          {inputError && <p id={`${panelId}-error`} className="chat-input-error" role="alert">{inputError}</p>}
        </form>
        <div className="chat-sr-only" role="status" aria-live="polite" aria-atomic="true">
          {chatSession.announcement}
        </div>
      </section>
      <button ref={launcher} className="chat-launcher" type="button" aria-label={open ? 'Collapse chat' : 'Open chat'}
        aria-expanded={open} aria-controls={panelId} onClick={() => { if (open) close(); else setOpen(true); }}>
        <svg viewBox="0 0 32 32" aria-hidden="true"><path d="M27 15a11 11 0 0 1-11 11 12 12 0 0 1-5-1l-6 2 2-6a11 11 0 1 1 20-6Z" /></svg>
      </button>
    </div>
  );
}
