import SwiftUI
import sharedKit

struct ContentView: View {
    @StateObject private var viewModel = ConnectionViewModel()
    
    var body: some View {
        NavigationView {
            if viewModel.isConnected {
                MainControlView(viewModel: viewModel)
            } else {
                ConnectionView(viewModel: viewModel)
            }
        }
        .accentColor(.cyan)
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
