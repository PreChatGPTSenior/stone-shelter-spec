import { ChatStreamMockScenario } from '../../enums/ChatStreamMockScenario';

export const chatStreamScenarios = {
  [ChatStreamMockScenario.Success]: { chunkCodePoints: 8 },
  [ChatStreamMockScenario.Interrupted]: { chunkCodePoints: 8 },
  [ChatStreamMockScenario.Error]: { chunkCodePoints: 8 },
};
