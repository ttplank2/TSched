/**
 * Lets a screen that scrolls be scrolled by holding the mouse button down and dragging, and keep coasting after the
 * mouse is let go, like a finger on a phone. (Touch screens already scroll this way by themselves.)
 */

const SLOP = 8;
/** Controls that use dragging themselves; a drag that starts on one is left alone. */
const OWN_DRAG = 'input, textarea, select, .np, .sl, .wheel, .clock';

export function dragScroll(container) {
  let gesture = null;
  let coastFrame = null;
  let justDragged = false;

  const stop = () => {
    if (coastFrame !== null) {
      cancelAnimationFrame(coastFrame);
      coastFrame = null;
    }
  };

  const coast = speed => {
    let velocity = speed;
    let last = performance.now();
    const step = now => {
      const dt = Math.min(0.05, (now - last) / 1000);
      last = now;
      const before = container.scrollTop;
      container.scrollTop = before - velocity * dt;
      velocity *= Math.exp(-3 * dt);
      coastFrame = Math.abs(velocity) > 20 && container.scrollTop !== before ? requestAnimationFrame(step) : null;
    };
    coastFrame = requestAnimationFrame(step);
  };

  container.addEventListener('pointerdown', event => {
    if (event.pointerType !== 'mouse' || event.button !== 0 || event.target.closest(OWN_DRAG)) {
      return;
    }
    stop();
    gesture = { y: event.clientY, top: container.scrollTop, dragging: false, samples: [] };
  });

  container.addEventListener('pointermove', event => {
    if (!gesture) {
      return;
    }
    const dy = event.clientY - gesture.y;
    if (!gesture.dragging && Math.abs(dy) > SLOP) {
      gesture.dragging = true;
      container.setPointerCapture(event.pointerId);
    }
    if (gesture.dragging) {
      container.scrollTop = gesture.top - dy;
      gesture.samples.push({ t: performance.now(), y: event.clientY });
      gesture.samples = gesture.samples.filter(sample => sample.t > performance.now() - 120);
    }
  });

  const release = event => {
    if (!gesture) {
      return;
    }
    const finished = gesture;
    gesture = null;
    if (!finished.dragging) {
      return;
    }
    // The click that ends a drag must not press whatever button the mouse is over.
    justDragged = true;
    setTimeout(() => { justDragged = false; }, 0);
    const first = finished.samples[0];
    const seconds = first ? (performance.now() - first.t) / 1000 : 0;
    const speed = seconds > 0.01 ? (event.clientY - first.y) / seconds : 0;
    if (Math.abs(speed) > 150) {
      coast(Math.max(-4000, Math.min(4000, speed)));
    }
  };
  container.addEventListener('pointerup', release);
  container.addEventListener('pointercancel', release);
  container.addEventListener('click', event => {
    if (justDragged) {
      event.stopPropagation();
      event.preventDefault();
    }
  }, true);
}
