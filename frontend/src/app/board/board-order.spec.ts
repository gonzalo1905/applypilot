import { cardsIn, moveCard, removeCard } from './board-order';
import { card, layout } from './testing/application.fixtures';

describe('board order', () => {
  const board = () => [
    card('a', 'SAVED', 0),
    card('b', 'SAVED', 1),
    card('c', 'SAVED', 2),
    card('d', 'INTERVIEW', 0),
  ];

  describe('cardsIn', () => {
    it('returns one column sorted by position', () => {
      const cards = [card('b', 'SAVED', 1), card('x', 'OFFER', 0), card('a', 'SAVED', 0)];

      expect(cardsIn(cards, 'SAVED').map((item) => item.id)).toEqual(['a', 'b']);
    });

    it('breaks a position tie by creation time', () => {
      const older = card('older', 'SAVED', 0);
      const newer = card('newer', 'SAVED', 0);

      expect(cardsIn([newer, older], 'SAVED').map((item) => item.id)).toEqual(['older', 'newer']);
    });
  });

  describe('moveCard', () => {
    it('moves a card down inside its column and renumbers it', () => {
      const result = moveCard(board(), 'a', 'SAVED', 2);

      expect(layout(result, 'SAVED')).toEqual(['b:0', 'c:1', 'a:2']);
    });

    it('moves a card up inside its column', () => {
      const result = moveCard(board(), 'c', 'SAVED', 0);

      expect(layout(result, 'SAVED')).toEqual(['c:0', 'a:1', 'b:2']);
    });

    it('moves a card to another column and closes the gap it leaves', () => {
      const result = moveCard(board(), 'a', 'INTERVIEW', 0);

      expect(layout(result, 'SAVED')).toEqual(['b:0', 'c:1']);
      expect(layout(result, 'INTERVIEW')).toEqual(['a:0', 'd:1']);
    });

    it('treats an index past the end as "last"', () => {
      const result = moveCard(board(), 'a', 'INTERVIEW', 99);

      expect(layout(result, 'INTERVIEW')).toEqual(['d:0', 'a:1']);
    });

    it('moves a card into an empty column', () => {
      const result = moveCard(board(), 'd', 'OFFER', 0);

      expect(layout(result, 'OFFER')).toEqual(['d:0']);
      expect(layout(result, 'INTERVIEW')).toEqual([]);
    });

    it('returns the same array when the card is dropped where it already was', () => {
      const cards = board();

      expect(moveCard(cards, 'b', 'SAVED', 1)).toBe(cards);
    });

    it('returns the same array for an unknown card', () => {
      const cards = board();

      expect(moveCard(cards, 'missing', 'OFFER', 0)).toBe(cards);
    });

    it('does not mutate the original cards', () => {
      const cards = board();
      const snapshot = JSON.stringify(cards);

      moveCard(cards, 'a', 'INTERVIEW', 0);

      expect(JSON.stringify(cards)).toBe(snapshot);
    });
  });

  describe('removeCard', () => {
    it('removes the card and renumbers the rest of its column', () => {
      const result = removeCard(board(), 'a');

      expect(layout(result, 'SAVED')).toEqual(['b:0', 'c:1']);
      expect(layout(result, 'INTERVIEW')).toEqual(['d:0']);
    });
  });
});
