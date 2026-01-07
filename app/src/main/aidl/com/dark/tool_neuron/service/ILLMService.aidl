package com.dark.tool_neuron.service;

import com.dark.tool_neuron.service.IGgufGenerationCallback;

interface ILLMService {

    //Gguf
    boolean loadGgufModel(String modelPath, String modelName, String loadingParams, String inferenceParams);
    void generateGguf(String prompt, int maxTokens, IGgufGenerationCallback callback);
    void stopGenerationGguf();
    void unloadModelGguf();
    String getModelInfoGguf();

}