#version 450

// Vertex shader "fullscreen triangle" clássico: desenha um triângulo maior que o ecrã a partir de
// 3 vértices gerados só com gl_VertexIndex (sem vertex buffer nenhum -- comando
// vkCmdDraw(cmd, 3, 1, 0, 0)). É o padrão universal para passes de pós-processamento em Vulkan
// (usado pelo vkBasalt, pelas camadas de validação, etc.): cobre o ecrã inteiro com o mínimo de
// overhead possível, sem precisar de alocar/vincular geometria.
layout(location = 0) out vec2 fragTexCoord;

void main() {
    // vertex 0 -> (-1,-1), vertex 1 -> (3,-1), vertex 2 -> (-1,3): um triângulo que cobre todo o
    // NDC [-1,1]x[-1,1] e ultrapassa-o de propósito (é cortado pelo rasterizador).
    vec2 pos = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    fragTexCoord = pos;
    gl_Position = vec4(pos * 2.0 - 1.0, 0.0, 1.0);
}
