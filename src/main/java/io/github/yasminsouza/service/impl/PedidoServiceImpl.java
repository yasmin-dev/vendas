package io.github.yasminsouza.service.impl;

import io.github.yasminsouza.dto.InformacoesItemPedidoDTO;
import io.github.yasminsouza.dto.InformacoesPedidoDTO;
import io.github.yasminsouza.dto.ItemPedidoDTO;
import io.github.yasminsouza.dto.PedidoDTO;
import io.github.yasminsouza.enums.StatusPedido;
import io.github.yasminsouza.exception.NotFoundException;
import io.github.yasminsouza.exception.RegraNegocioException;
import io.github.yasminsouza.model.Client;
import io.github.yasminsouza.model.ItemPedido;
import io.github.yasminsouza.model.Pedido;
import io.github.yasminsouza.model.Product;
import io.github.yasminsouza.repository.ClientRepository;
import io.github.yasminsouza.repository.ItemPedidoRepository;
import io.github.yasminsouza.repository.PedidoRepository;
import io.github.yasminsouza.repository.ProductRepository;
import io.github.yasminsouza.service.PedidoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PedidoServiceImpl implements PedidoService {

    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final PedidoRepository pedidoRepository;

    @Override
    @Transactional
    public Pedido salvar(PedidoDTO dto) {
        Client client = clientRepository
                .findById(dto.getCliente())
                .orElseThrow(() -> new RegraNegocioException("Código de cliente inválido"));

        Pedido pedido = Pedido.builder()
            .client(client)
            .dataPedido(LocalDate.now())
            .status(StatusPedido.REALIZADO)
            .build();

        List<ItemPedido> itens = getItens(dto.getItens(), pedido);
        pedidoRepository.save(pedido);
        itemPedidoRepository.saveAll(itens);
        pedido.setItens(itens);
        pedido.setTotal(getTotal(itens));
        return pedido;
    }

    private BigDecimal getTotal(List<ItemPedido> itens) {
        return itens.stream().map(itemPedido -> {
            return itemPedido.getProduct().getPrice().multiply(new BigDecimal(itemPedido.getQuantidade()));
        }).findAny().get();
    }

    @Override
    public InformacoesPedidoDTO obterPedido(Integer codPedido) {
        Pedido pedido = pedidoRepository
                .findByIdFetchItens(codPedido)
                .orElseThrow(() -> new RegraNegocioException("Código de pedido inválido"));

        return InformacoesPedidoDTO.builder()
            .id(pedido.getId())
            .cliente(pedido.getClient().getName())
            .data(pedido.getDataPedido())
            .status(pedido.getStatus())
            .total(pedido.getTotal())
            .itens(converterItens(pedido.getItens()))
            .build();
    }

    @Override
    @Transactional
    public void atualizarStatusPedido(Integer id, StatusPedido novoStatus) {
        pedidoRepository.findById(id)
                .map(pedido -> {
                    pedido.setStatus(novoStatus);
                    return pedidoRepository.save(pedido);
                })
                .orElseThrow(() -> new NotFoundException("Pedido não encontrado."));
    }

    private List<InformacoesItemPedidoDTO> converterItens(List<ItemPedido> itensPedido) {
        return itensPedido.stream().map(itemPedido -> InformacoesItemPedidoDTO.builder()
                .produto(itemPedido.getProduct().getDescription())
                .preco(itemPedido.getProduct().getPrice())
                .quantidade(itemPedido.getQuantidade())
                .build()).collect(Collectors.toList());
    }

    private List<ItemPedido> getItens(List<ItemPedidoDTO> itensPedidoDTO, Pedido pedido) {

        if(itensPedidoDTO.isEmpty())
            new RegraNegocioException("Lista de itens vazia");

        return itensPedidoDTO.stream().map(itemDTO -> {
            ItemPedido item = new ItemPedido();
            Product product = productRepository
                    .findById(itemDTO.getProduto())
                    .orElseThrow(() -> new RegraNegocioException("Código de produto inválido"));
            item.setPedido(pedido);
            item.setProduct(product);
            item.setQuantidade(itemDTO.getQuantidade());
            return item;
        }).collect(Collectors.toList());
    }
}
